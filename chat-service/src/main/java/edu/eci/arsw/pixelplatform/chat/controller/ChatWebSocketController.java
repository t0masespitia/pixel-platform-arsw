package edu.eci.arsw.pixelplatform.chat.controller;

import edu.eci.arsw.pixelplatform.chat.client.CanvasAccessClient;
import edu.eci.arsw.pixelplatform.chat.dto.ChatMessageCommand;
import edu.eci.arsw.pixelplatform.chat.dto.ChatMessageResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.UUID;

@Controller
public class ChatWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class);

    private final CanvasAccessClient canvasAccessClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final Counter messagesSentCounter;

    public ChatWebSocketController(CanvasAccessClient canvasAccessClient,
                                   SimpMessagingTemplate messagingTemplate,
                                   MeterRegistry meterRegistry) {
        this.canvasAccessClient = canvasAccessClient;
        this.messagingTemplate = messagingTemplate;
        this.messagesSentCounter = Counter.builder("pixelplatform.messages.sent")
                .description("Total de mensajes enviados")
                .tag("channel", "canvas")
                .register(meterRegistry);
    }

    @MessageMapping("/chat/{canvasId}/send")
    public void sendMessage(@DestinationVariable UUID canvasId,
                            @Valid ChatMessageCommand command,
                            SimpMessageHeaderAccessor headerAccessor) {
        String verifiedUserId = (String) headerAccessor.getSessionAttributes().get("verifiedUserId");
        if (!command.userId().equals(verifiedUserId)) {
            log.warn("Mensaje rechazado [canvas={} user={}]: userId del comando no coincide con el token",
                    canvasId, command.userId());
            return;
        }
        String bearerToken = (String) headerAccessor.getSessionAttributes().get("bearerToken");
        if (!canvasAccessClient.hasAccess(canvasId, command.userId(), bearerToken)) {
            log.warn("Mensaje rechazado [canvas={} user={}]: sin acceso al lienzo", canvasId, command.userId());
            return;
        }
        messagesSentCounter.increment();
        ChatMessageResponse response = new ChatMessageResponse(command.userId(), command.message(), Instant.now());
        messagingTemplate.convertAndSend("/topic/chat/" + canvasId, response);
    }
}
