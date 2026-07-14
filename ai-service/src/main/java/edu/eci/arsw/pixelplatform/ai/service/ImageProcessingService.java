package edu.eci.arsw.pixelplatform.ai.service;

import org.springframework.stereotype.Service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

@Service
public class ImageProcessingService {

    /**
     * Paleta de colores de la plataforma. Solo se usa en generateDitheredTemplate
     * (modo "color"). DEBE mantenerse sincronizada a mano con el array PALETTE de
     * frontend/src/components/ColorPalette.jsx (mismo orden, mismos 16 colores).
     * Si se agrega o quita un color alla, hay que replicarlo aca tambien.
     */
    private static final int[][] PALETTE = {
            {0x00, 0x00, 0x00}, // Negro
            {0xFF, 0xFF, 0xFF}, // Blanco
            {0x80, 0x80, 0x80}, // Gris
            {0xC0, 0xC0, 0xC0}, // Gris claro
            {0xFF, 0x00, 0x00}, // Rojo
            {0xFF, 0x88, 0x00}, // Naranja
            {0xFF, 0xFF, 0x00}, // Amarillo
            {0x00, 0xCC, 0x00}, // Verde
            {0x00, 0xCC, 0xCC}, // Cyan
            {0x00, 0x00, 0xFF}, // Azul
            {0x88, 0x00, 0xFF}, // Violeta
            {0xEC, 0x48, 0x99}, // Magenta
            {0x8B, 0x45, 0x13}, // Marron
            {0xFF, 0x69, 0xB4}, // Rosa
            {0x00, 0x64, 0x00}, // Verde oscuro
            {0x00, 0x00, 0x80}, // Azul marino
    };

    /**
     * Que tan cerca del blanco se lleva cada canal en el modo "muy clarito".
     * 0.0 = color original sin cambios, 1.0 = blanco puro. 0.8 significa que se
     * nota claramente el matiz original pero muy aclarado, como una guia suave.
     */
    private static final double LIGHT_TINT_AMOUNT = 0.8;

    /**
     * Modo "grises": escala de grises continua (formula de luminancia estandar),
     * SIN cuantizar contra ninguna paleta fija y SIN dithering. Al ser un valor
     * continuo por pixel (cualquier hex, no limitado a los 16 swatches), se ve
     * suave y sin ruido de puntos.
     */
    public Map<String, String> generateGrayscaleTemplate(BufferedImage source, int targetWidth, int targetHeight) {
        return processContinuous(source, targetWidth, targetHeight, (r, g, b) -> {
            int gray = clampInt((int) Math.round(0.299 * r + 0.587 * g + 0.114 * b));
            return new int[]{gray, gray, gray};
        });
    }

    /**
     * Modo "muy clarito": conserva el matiz de color original de cada pixel pero
     * lo aclara fuertemente hacia el blanco (ver LIGHT_TINT_AMOUNT). Igual que el
     * modo de grises, es continuo (no limitado a los 16 swatches) y sin dithering,
     * para que se vea limpio y no como ruido de puntos de color.
     */
    public Map<String, String> generateLightTintTemplate(BufferedImage source, int targetWidth, int targetHeight) {
        return processContinuous(source, targetWidth, targetHeight, (r, g, b) ->
                new int[]{lighten(r), lighten(g), lighten(b)});
    }

    /**
     * Modo "color": cuantiza cada pixel contra la paleta real de la plataforma
     * (PALETTE de arriba) y aplica dithering Floyd-Steinberg (difusion de error)
     * para aproximar el tono original con solo 16 colores disponibles. A
     * diferencia de los otros dos modos, aca SI hay ruido de puntos visible por
     * diseno: es el precio de estar limitado a 16 colores exactos y pintables con
     * un clic. Sigue sin ser IA real: es procesamiento de imagen convencional.
     * (Comportamiento identico al que ya tenia, solo se extrajo el escalado a un
     * helper compartido con los otros dos modos).
     */
    public Map<String, String> generateDitheredTemplate(BufferedImage source, int targetWidth, int targetHeight) {
        ScaledImage scaled = scaleContain(source, targetWidth, targetHeight);

        double[][][] work = new double[scaled.height][scaled.width][3];
        for (int y = 0; y < scaled.height; y++) {
            for (int x = 0; x < scaled.width; x++) {
                int rgb = scaled.image.getRGB(x, y);
                work[y][x][0] = (rgb >> 16) & 0xFF;
                work[y][x][1] = (rgb >> 8) & 0xFF;
                work[y][x][2] = rgb & 0xFF;
            }
        }

        Map<String, String> pixels = new HashMap<>(scaled.width * scaled.height);
        for (int y = 0; y < scaled.height; y++) {
            for (int x = 0; x < scaled.width; x++) {
                double oldR = clamp(work[y][x][0]);
                double oldG = clamp(work[y][x][1]);
                double oldB = clamp(work[y][x][2]);

                int[] nearest = nearestPaletteColor(oldR, oldG, oldB);

                double errR = oldR - nearest[0];
                double errG = oldG - nearest[1];
                double errB = oldB - nearest[2];

                diffuseError(work, x + 1, y, scaled.width, scaled.height, errR, errG, errB, 7.0 / 16.0);
                diffuseError(work, x - 1, y + 1, scaled.width, scaled.height, errR, errG, errB, 3.0 / 16.0);
                diffuseError(work, x, y + 1, scaled.width, scaled.height, errR, errG, errB, 5.0 / 16.0);
                diffuseError(work, x + 1, y + 1, scaled.width, scaled.height, errR, errG, errB, 1.0 / 16.0);

                int canvasX = x + scaled.offsetX;
                int canvasY = y + scaled.offsetY;
                pixels.put(canvasX + "," + canvasY,
                        String.format("#%02X%02X%02X", nearest[0], nearest[1], nearest[2]));
            }
        }
        return pixels;
    }

    // --- Helpers compartidos ---

    @FunctionalInterface
    private interface PixelTransform {
        int[] apply(int r, int g, int b);
    }

    private Map<String, String> processContinuous(BufferedImage source, int targetWidth, int targetHeight,
                                                    PixelTransform transform) {
        ScaledImage scaled = scaleContain(source, targetWidth, targetHeight);
        Map<String, String> pixels = new HashMap<>(scaled.width * scaled.height);
        for (int y = 0; y < scaled.height; y++) {
            for (int x = 0; x < scaled.width; x++) {
                int rgb = scaled.image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int[] out = transform.apply(r, g, b);
                int canvasX = x + scaled.offsetX;
                int canvasY = y + scaled.offsetY;
                pixels.put(canvasX + "," + canvasY,
                        String.format("#%02X%02X%02X", out[0], out[1], out[2]));
            }
        }
        return pixels;
    }

    private ScaledImage scaleContain(BufferedImage source, int targetWidth, int targetHeight) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();

        double scale = Math.min(
                (double) targetWidth / sourceWidth,
                (double) targetHeight / sourceHeight
        );
        int scaledWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int scaledHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
        int offsetX = (targetWidth - scaledWidth) / 2;
        int offsetY = (targetHeight - scaledHeight) / 2;

        BufferedImage scaled = new BufferedImage(scaledWidth, scaledHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(source, 0, 0, scaledWidth, scaledHeight, null);
        g2d.dispose();

        return new ScaledImage(scaled, scaledWidth, scaledHeight, offsetX, offsetY);
    }

    private static void diffuseError(double[][][] work, int x, int y, int width, int height,
                                      double errR, double errG, double errB, double factor) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return;
        }
        work[y][x][0] += errR * factor;
        work[y][x][1] += errG * factor;
        work[y][x][2] += errB * factor;
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int clampInt(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private int lighten(int channel) {
        return clampInt((int) Math.round(channel + (255 - channel) * LIGHT_TINT_AMOUNT));
    }

    private static int[] nearestPaletteColor(double r, double g, double b) {
        int[] best = PALETTE[0];
        double bestDist = Double.MAX_VALUE;
        for (int[] candidate : PALETTE) {
            double dr = r - candidate[0];
            double dg = g - candidate[1];
            double db = b - candidate[2];
            double dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    private static final class ScaledImage {
        final BufferedImage image;
        final int width;
        final int height;
        final int offsetX;
        final int offsetY;

        ScaledImage(BufferedImage image, int width, int height, int offsetX, int offsetY) {
            this.image = image;
            this.width = width;
            this.height = height;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }
}
