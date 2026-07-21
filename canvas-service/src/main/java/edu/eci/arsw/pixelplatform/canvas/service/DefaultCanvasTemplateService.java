package edu.eci.arsw.pixelplatform.canvas.service;

import edu.eci.arsw.pixelplatform.canvas.dto.CanvasResponse;
import edu.eci.arsw.pixelplatform.canvas.model.Canvas;
import edu.eci.arsw.pixelplatform.canvas.model.CanvasMembership;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasMembershipRepository;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Crea los 4 lienzos privados predeterminados que recibe cada usuario al
 * verificar su correo. Cada uno se pinta con una de las 4 imagenes de
 * classpath:default-templates/template-{1..4}.png usando cuantizacion a escala
 * de grises + dithering Floyd-Steinberg, copiado aca para no depender de una
 * llamada HTTP a ai-service durante la verificacion de correo.
 *
 * Nota: el metodo que crea cada lienzo individual NO usa @Transactional a
 * proposito. Se invoca desde dentro de esta misma clase (self-invocation),
 * y el proxy de Spring no intercepta llamadas internas, asi que la
 * anotacion no tendria efecto real. En su lugar, cada uno de los 4 lienzos
 * se crea en su propio try/catch: si uno falla (ej. imagen corrupta), los
 * otros 3 igual quedan creados.
 */
@Service
public class DefaultCanvasTemplateService {

    private static final Logger log = LoggerFactory.getLogger(DefaultCanvasTemplateService.class);
    private static final int TEMPLATE_COUNT = 4;

    private static final String[] TEMPLATE_NAMES = {
            "pinta a stitch",
            "Pinta a pikachu",
            "pinta a un perrito",
            "pinta al pajaro carpintero"
    };

    private static final int[][] PALETTE = {
            {0x00, 0x00, 0x00},
            {0x80, 0x80, 0x80},
            {0xC0, 0xC0, 0xC0},
            {0xFF, 0xFF, 0xFF},
    };

    private final CanvasRepository canvasRepository;
    private final CanvasMembershipRepository canvasMembershipRepository;
    private final CanvasStateService canvasStateService;

    @Value("${canvas.default-templates.width}")
    private int templateWidth;

    @Value("${canvas.default-templates.height}")
    private int templateHeight;

    public DefaultCanvasTemplateService(CanvasRepository canvasRepository,
                                         CanvasMembershipRepository canvasMembershipRepository,
                                         CanvasStateService canvasStateService) {
        this.canvasRepository = canvasRepository;
        this.canvasMembershipRepository = canvasMembershipRepository;
        this.canvasStateService = canvasStateService;
    }

    public List<CanvasResponse> createDefaultCanvasesForUser(String ownerId) {
        List<Canvas> existentes = canvasRepository.findByOwnerId(ownerId).stream()
                .filter(Canvas::isDefaultTemplate)
                .collect(Collectors.toList());
        if (existentes.size() >= TEMPLATE_COUNT) {
            log.info("El usuario {} ya tiene sus {} lienzos predeterminados, no se crean de nuevo",
                    ownerId, TEMPLATE_COUNT);
            return existentes.stream().map(this::toResponse).collect(Collectors.toList());
        }

        List<CanvasResponse> creados = new ArrayList<>();
        for (int i = 1; i <= TEMPLATE_COUNT; i++) {
            try {
                creados.add(crearUnLienzoPredeterminado(ownerId, i));
            } catch (Exception e) {
                log.error("No se pudo crear el lienzo predeterminado {} para el usuario {}: {}",
                        i, ownerId, e.getMessage(), e);
            }
        }
        return creados;
    }

    private CanvasResponse crearUnLienzoPredeterminado(String ownerId, int templateIndex) throws IOException {
        BufferedImage source = leerImagenDePlantilla(templateIndex);

        Canvas canvas = new Canvas();
        canvas.setId(UUID.randomUUID());
        canvas.setName(TEMPLATE_NAMES[templateIndex - 1]);
        canvas.setOwnerId(ownerId);
        canvas.setWidth(templateWidth);
        canvas.setHeight(templateHeight);
        canvas.setPrivate(true);
        canvas.setDefaultTemplate(true);
        canvas.setCreatedAt(Instant.now());
        Canvas guardado = canvasRepository.save(canvas);

        CanvasMembership membership = CanvasMembership.builder()
                .id(UUID.randomUUID())
                .canvasId(guardado.getId())
                .userId(ownerId)
                .joinedAt(Instant.now())
                .build();
        canvasMembershipRepository.save(membership);

        Map<String, String> pixeles = generarPlantillaConDithering(source, templateWidth, templateHeight);
        canvasStateService.bulkSetPixels(guardado.getId(), ownerId, pixeles);

        return toResponse(guardado);
    }

    private BufferedImage leerImagenDePlantilla(int index) throws IOException {
        String path = "default-templates/template-" + index + ".png";
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IOException("No se pudo decodificar " + path + " como imagen");
            }
            return image;
        }
    }

    private CanvasResponse toResponse(Canvas canvas) {
        return new CanvasResponse(
                canvas.getId(), canvas.getName(), canvas.getOwnerId(),
                canvas.getWidth(), canvas.getHeight(), canvas.isPrivate(),
                canvas.isDefaultTemplate(), canvas.getCreatedAt());
    }

    // --- Dithering: copia de ai-service ImageProcessingService.generateDitheredTemplate ---

    private Map<String, String> generarPlantillaConDithering(BufferedImage source, int targetWidth, int targetHeight) {
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

    private ScaledImage scaleContain(BufferedImage source, int targetWidth, int targetHeight) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();

        double scale = Math.min((double) targetWidth / sourceWidth, (double) targetHeight / sourceHeight);
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
