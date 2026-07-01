package edu.eci.arsw.pixelplatform.canvas.controller;

import edu.eci.arsw.pixelplatform.canvas.dto.PixelDTO;
import edu.eci.arsw.pixelplatform.canvas.dto.PixelPaintCommand;
import edu.eci.arsw.pixelplatform.canvas.exception.CooldownActiveException;
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
    public PixelDTO handlePixel(@Valid PixelPaintCommand command) {
        try {
            canvasStateService.paintPixelWithCooldown(command);
            return new PixelDTO(command.x(), command.y(), command.color());
        } catch (IllegalArgumentException | CooldownActiveException e) {
            log.warn("Pixel rechazado [user={}]: {}", command.userId(), e.getMessage());
            return null;
        }
    }
}
