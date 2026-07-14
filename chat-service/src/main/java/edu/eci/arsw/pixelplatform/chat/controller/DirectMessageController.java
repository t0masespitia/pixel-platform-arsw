package edu.eci.arsw.pixelplatform.chat.controller;

import edu.eci.arsw.pixelplatform.chat.dto.DirectMessageResponse;
import edu.eci.arsw.pixelplatform.chat.dto.SendCanvasInvitationMessageRequest;
import edu.eci.arsw.pixelplatform.chat.dto.SendDirectMessageRequest;
import edu.eci.arsw.pixelplatform.chat.service.DirectMessageService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class DirectMessageController {

    private final DirectMessageService directMessageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final Counter directMessagesSentCounter;

    public DirectMessageController(DirectMessageService directMessageService,
                                   SimpMessagingTemplate messagingTemplate,
                                   MeterRegistry meterRegistry) {
        this.directMessageService = directMessageService;
        this.messagingTemplate = messagingTemplate;
        this.directMessagesSentCounter = Counter.builder("pixelplatform.messages.sent")
                .description("Total de mensajes enviados")
                .tag("channel", "direct")
                .register(meterRegistry);
    }

    @PostMapping
    public ResponseEntity<DirectMessageResponse> sendMessage(
            @Valid @RequestBody SendDirectMessageRequest request,
            HttpServletRequest httpRequest) {
        String fromUserId = (String) httpRequest.getAttribute("verifiedUserId");
        DirectMessageResponse response = directMessageService.sendMessage(fromUserId, request);
        messagingTemplate.convertAndSend("/topic/dm/" + request.toUserId(), response);
        directMessagesSentCounter.increment();
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/canvas-invitation")
    public ResponseEntity<DirectMessageResponse> sendCanvasInvitationMessage(
            @Valid @RequestBody SendCanvasInvitationMessageRequest request,
            HttpServletRequest httpRequest) {
        String fromUserId = (String) httpRequest.getAttribute("verifiedUserId");
        DirectMessageResponse response = directMessageService.sendCanvasInvitationMessage(fromUserId, request);
        messagingTemplate.convertAndSend("/topic/dm/" + request.toUserId(), response);
        directMessagesSentCounter.increment();
        return ResponseEntity.status(201).body(response);
    }

    @GetMapping("/{otherUserId}")
    public ResponseEntity<List<DirectMessageResponse>> getConversation(
            @PathVariable String otherUserId,
            HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("verifiedUserId");
        return ResponseEntity.ok(directMessageService.getConversation(userId, otherUserId));
    }
}
