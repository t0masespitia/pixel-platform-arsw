package edu.eci.arsw.pixelplatform.ai.controller;

import edu.eci.arsw.pixelplatform.ai.client.CanvasServiceClient;
import edu.eci.arsw.pixelplatform.ai.dto.GenerateTemplateResponse;
import edu.eci.arsw.pixelplatform.ai.service.ImageProcessingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai")
public class AiTemplateController {

    private final CanvasServiceClient canvasServiceClient;
    private final ImageProcessingService imageProcessingService;
    private final Counter templatesGeneratedCounter;

    public AiTemplateController(CanvasServiceClient canvasServiceClient,
                                 ImageProcessingService imageProcessingService,
                                 MeterRegistry meterRegistry) {
        this.canvasServiceClient = canvasServiceClient;
        this.imageProcessingService = imageProcessingService;
        this.templatesGeneratedCounter = Counter.builder("pixelplatform.ai.templates.generated")
                .description("Total de plantillas de IA generadas")
                .register(meterRegistry);
    }

    @PostMapping(value = "/canvases/{canvasId}/generate-template",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> generateTemplate(
            @PathVariable UUID canvasId,
            @RequestParam("requesterId") String requesterId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "COLOR") String mode,
            HttpServletRequest httpRequest) {
        try {
            String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
            if (!verifiedUserId.equals(requesterId)) {
                return ResponseEntity.status(403).body(Map.of("error",
                        "El usuario del token no coincide con el userId de la peticion"));
            }
            String bearerToken = httpRequest.getHeader("Authorization");
            if (file.isEmpty()) {
                throw new IllegalArgumentException("El archivo esta vacio");
            }
            CanvasServiceClient.CanvasInfo canvas = canvasServiceClient.getCanvas(canvasId, bearerToken);
            if (!canvas.isPrivate()) {
                throw new IllegalArgumentException("Solo los lienzos privados admiten plantillas de IA");
            }
            if (!requesterId.equals(canvas.ownerId())) {
                throw new IllegalArgumentException("Solo el dueno del lienzo puede generar una plantilla");
            }
            if (canvas.isDefaultTemplate()) {
                throw new IllegalArgumentException(
                        "Este lienzo ya tiene una imagen predeterminada, no admite generar otra");
            }
            BufferedImage source;
            try {
                source = ImageIO.read(file.getInputStream());
            } catch (IOException e) {
                throw new IllegalArgumentException("No se pudo leer el archivo como imagen");
            }
            if (source == null) {
                throw new IllegalArgumentException(
                        "El archivo no es una imagen valida (formatos soportados: PNG, JPG, GIF, BMP)");
            }
            Map<String, String> template = switch (mode) {
                case "GRAYSCALE" -> imageProcessingService.generateGrayscaleTemplate(
                        source, canvas.width(), canvas.height());
                case "LIGHT" -> imageProcessingService.generateLightTintTemplate(
                        source, canvas.width(), canvas.height());
                case "COLOR" -> imageProcessingService.generateDitheredTemplate(
                        source, canvas.width(), canvas.height());
                default -> throw new IllegalArgumentException(
                        "El modo debe ser GRAYSCALE, LIGHT o COLOR");
            };
            canvasServiceClient.bulkSetPixels(canvasId, requesterId, template, bearerToken);
            templatesGeneratedCounter.increment();
            return ResponseEntity.ok(new GenerateTemplateResponse(
                    canvasId, template.size(), canvas.width(), canvas.height()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
