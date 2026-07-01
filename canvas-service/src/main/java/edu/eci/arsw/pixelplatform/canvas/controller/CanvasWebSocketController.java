package edu.eci.arsw.pixelplatform.canvas.controller;

import edu.eci.arsw.pixelplatform.canvas.dto.PixelDTO;
import edu.eci.arsw.pixelplatform.canvas.service.CanvasStateService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class CanvasWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(CanvasWebSocketController.class);

    private final CanvasStateService canvasStateService;

    public CanvasWebSocketController(CanvasStateService canvasStateService) {
        this.canvasStateService = canvasStateService;
    }

    @MessageMapping("/canvas/pixel")
    @SendTo("/topic/canvas")
    public PixelDTO handlePixel(@Valid PixelDTO pixel) {
        try {
            canvasStateService.paintPixel(pixel);
            return pixel;
        } catch (IllegalArgumentException e) {
            log.warn("Pixel rechazado por coordenadas invalidas: {}", e.getMessage());
            return null;
        }
    }
}
