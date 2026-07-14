package edu.eci.arsw.pixelplatform.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ImageProcessingServiceTest {

    private ImageProcessingService service;

    private static final Set<String> PALETTE_HEX = Set.of(
            "#000000", "#FFFFFF", "#808080", "#C0C0C0",
            "#FF0000", "#FF8800", "#FFFF00", "#00CC00",
            "#00CCCC", "#0000FF", "#8800FF", "#EC4899",
            "#8B4513", "#FF69B4", "#006400", "#000080"
    );

    @BeforeEach
    void setUp() {
        service = new ImageProcessingService();
    }

    // --- Modo COLOR (dithering + paleta) — mismos tests de antes, sin cambios ---

    @Test
    void pixelBlancoDaHexBlancoYNegroHexNegro() {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFFFFFF);
        img.setRGB(1, 0, 0xFFFFFF);
        img.setRGB(0, 1, 0x000000);
        img.setRGB(1, 1, 0x000000);

        Map<String, String> result = service.generateDitheredTemplate(img, 2, 2);

        assertEquals(4, result.size());
        assertEquals("#FFFFFF", result.get("0,0"));
        assertEquals("#FFFFFF", result.get("1,0"));
        assertEquals("#000000", result.get("0,1"));
        assertEquals("#000000", result.get("1,1"));
    }

    @Test
    void escalarImagenDos2aCuatro4ProduceDieciseisEntradas() {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFF0000);
        img.setRGB(1, 0, 0x00FF00);
        img.setRGB(0, 1, 0x0000FF);
        img.setRGB(1, 1, 0xFFFFFF);

        Map<String, String> result = service.generateDitheredTemplate(img, 4, 4);

        assertEquals(16, result.size());
    }

    @Test
    void imagenDeRetratoSeCentraSinDeformarNiRellenarMargenes() {
        BufferedImage img = new BufferedImage(2, 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 2; x++) {
                img.setRGB(x, y, 0xFFFFFF);
            }
        }

        Map<String, String> result = service.generateDitheredTemplate(img, 4, 4);

        assertEquals(8, result.size());
        for (int y = 0; y < 4; y++) {
            assertFalse(result.containsKey("0," + y));
            assertEquals("#FFFFFF", result.get("1," + y));
            assertEquals("#FFFFFF", result.get("2," + y));
            assertFalse(result.containsKey("3," + y));
        }
    }

    @Test
    void colorGrisConocidoCuantizaAlColorDePaletaMasCercano() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        int color = (100 << 16) | (150 << 8) | 200;
        img.setRGB(0, 0, color);

        Map<String, String> result = service.generateDitheredTemplate(img, 1, 1);

        assertEquals(1, result.size());
        assertEquals("#808080", result.get("0,0"));
    }

    @Test
    void grisIntermedioUniformeProduceDitheringConMasDeUnColor() {
        int width = 8;
        BufferedImage img = new BufferedImage(width, 1, BufferedImage.TYPE_INT_RGB);
        int gray = (64 << 16) | (64 << 8) | 64;
        for (int x = 0; x < width; x++) {
            img.setRGB(x, 0, gray);
        }

        Map<String, String> result = service.generateDitheredTemplate(img, width, 1);

        assertEquals(width, result.size());
        long distinctColors = result.values().stream().distinct().count();
        assertTrue(distinctColors > 1,
                "Se esperaba mas de un color por el dithering, se obtuvo: " + result.values());
        for (String hex : result.values()) {
            assertTrue(PALETTE_HEX.contains(hex), "Color fuera de paleta: " + hex);
        }
    }

    @Test
    void gradienteAmplioSoloProduceColoresDeLaPaleta() {
        int width = 64;
        BufferedImage img = new BufferedImage(width, 1, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            int v = (int) Math.round(x * 255.0 / (width - 1));
            int color = (v << 16) | (v << 8) | v;
            img.setRGB(x, 0, color);
        }

        Map<String, String> result = service.generateDitheredTemplate(img, width, 1);

        assertEquals(width, result.size());
        for (String hex : result.values()) {
            assertTrue(PALETTE_HEX.contains(hex), "Color fuera de paleta: " + hex);
        }
    }

    // --- Modo GRAYSCALE (continuo, sin dithering, sin cuantizar) ---

    @Test
    void modoGrisesPixelBlancoYNegroDanBlancoYNegro() {
        BufferedImage img = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFFFFFF);
        img.setRGB(1, 0, 0x000000);

        Map<String, String> result = service.generateGrayscaleTemplate(img, 2, 1);

        assertEquals("#FFFFFF", result.get("0,0"));
        assertEquals("#000000", result.get("1,0"));
    }

    @Test
    void modoGrisesColorConocidoDaLuminanciaContinua() {
        // RGB(100, 150, 200) -> gray = round(0.299*100 + 0.587*150 + 0.114*200)
        // = round(29.9 + 88.05 + 22.8) = round(140.75) = 141 = 0x8D
        // (continuo: NO se cuantiza contra la paleta, a diferencia del modo COLOR)
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        int color = (100 << 16) | (150 << 8) | 200;
        img.setRGB(0, 0, color);

        Map<String, String> result = service.generateGrayscaleTemplate(img, 1, 1);

        assertEquals("#8D8D8D", result.get("0,0"));
    }

    // --- Modo LIGHT (continuo, aclara hacia blanco conservando el matiz) ---

    @Test
    void modoClaritoNegroDaGrisClaroNoBlancoPuro() {
        // Negro aclarado 80% hacia blanco: 0 + 255*0.8 = 204 = 0xCC.
        // Se sigue notando la forma (no desaparece contra el fondo blanco).
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0x000000);

        Map<String, String> result = service.generateLightTintTemplate(img, 1, 1);

        assertEquals("#CCCCCC", result.get("0,0"));
    }

    @Test
    void modoClaritoBlancoSigueSiendoBlanco() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0xFFFFFF);

        Map<String, String> result = service.generateLightTintTemplate(img, 1, 1);

        assertEquals("#FFFFFF", result.get("0,0"));
    }

    @Test
    void modoClaritoColorConocidoAclaraConservandoElMatiz() {
        // RGB(100, 150, 200), aclarado 80% hacia blanco por canal:
        // R: 100 + (255-100)*0.8 = 100 + 124 = 224 = 0xE0
        // G: 150 + (255-150)*0.8 = 150 + 84  = 234 = 0xEA
        // B: 200 + (255-200)*0.8 = 200 + 44  = 244 = 0xF4
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        int color = (100 << 16) | (150 << 8) | 200;
        img.setRGB(0, 0, color);

        Map<String, String> result = service.generateLightTintTemplate(img, 1, 1);

        assertEquals("#E0EAF4", result.get("0,0"));
    }
}
