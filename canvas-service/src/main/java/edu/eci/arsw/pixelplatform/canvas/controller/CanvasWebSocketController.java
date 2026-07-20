package edu.eci.arsw.pixelplatform.canvas.controller;

import edu.eci.arsw.pixelplatform.canvas.dto.PixelDTO;
import edu.eci.arsw.pixelplatform.canvas.dto.PixelPaintCommand;
import edu.eci.arsw.pixelplatform.canvas.exception.CooldownActiveException;
import edu.eci.arsw.pixelplatform.canvas.service.CanvasStateService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
public class CanvasWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(CanvasWebSocketController.class);

    private final CanvasStateService canvasStateService;
    private final SimpMessagingTemplate messagingTemplate;

    public CanvasWebSocketController(CanvasStateService canvasStateService,
                                      SimpMessagingTemplate messagingTemplate) {
        this.canvasStateService = canvasStateService;
        this.messagingTemplate = messagingTemplate;
    }

    @SubscribeMapping("/canvas/{canvasId}/state")
    public Object getInitialState(@DestinationVariable UUID canvasId,
                                   SimpMessageHeaderAccessor headerAccessor) {
        String verifiedUserId = (String) headerAccessor.getSessionAttributes().get("verifiedUserId");
        log.info("Cliente solicitó snapshot canvas={} user={}", canvasId, verifiedUserId);
        return canvasStateService.getCanvasState(canvasId);
    }

    @MessageMapping("/canvas/{canvasId}/pixel")
    public void handlePixel(@DestinationVariable UUID canvasId,
                             @Valid PixelPaintCommand command,
                             SimpMessageHeaderAccessor headerAccessor) {
        String verifiedUserId = (String) headerAccessor.getSessionAttributes().get("verifiedUserId");
        if (!command.userId().equals(verifiedUserId)) {
            log.warn("WebSocket userId mismatch canvas={} token={} command={}", canvasId, verifiedUserId, command.userId());
            return;
        }
        log.debug("Pixel recibido canvas={} user={} x={} y={} color={}",
                canvasId, command.userId(), command.x(), command.y(), command.color());
        try {
            canvasStateService.paintPixelWithCooldown(canvasId, command);
            messagingTemplate.convertAndSend("/topic/canvas/" + canvasId,
                    new PixelDTO(command.x(), command.y(), command.color()));
            log.debug("Pixel confirmado canvas={} user={} x={} y={} color={}",
                    canvasId, command.userId(), command.x(), command.y(), command.color());
        } catch (IllegalArgumentException | CooldownActiveException e) {
            log.warn("Pixel rechazado canvas={} user={} x={} y={} color={} reason={}",
                    canvasId, command.userId(), command.x(), command.y(), command.color(), e.getMessage());
        }
    }
}
