package edu.eci.arsw.pixelplatform.signaling.controller;

import edu.eci.arsw.pixelplatform.signaling.client.CanvasAccessClient;
import edu.eci.arsw.pixelplatform.signaling.dto.IceCandidateCommand;
import edu.eci.arsw.pixelplatform.signaling.dto.IceCandidateEvent;
import edu.eci.arsw.pixelplatform.signaling.dto.PeerJoinedEvent;
import edu.eci.arsw.pixelplatform.signaling.dto.PeerLeftEvent;
import edu.eci.arsw.pixelplatform.signaling.dto.RoomActionCommand;
import edu.eci.arsw.pixelplatform.signaling.dto.RoomJoinedEvent;
import edu.eci.arsw.pixelplatform.signaling.dto.SdpAnswerEvent;
import edu.eci.arsw.pixelplatform.signaling.dto.SdpMessageCommand;
import edu.eci.arsw.pixelplatform.signaling.dto.SdpOfferEvent;
import edu.eci.arsw.pixelplatform.signaling.registry.SignalingRoomRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

@Controller
public class SignalingWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(SignalingWebSocketController.class);

    private final CanvasAccessClient canvasAccessClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final SignalingRoomRegistry registry;

    public SignalingWebSocketController(CanvasAccessClient canvasAccessClient,
                                        SimpMessagingTemplate messagingTemplate,
                                        SignalingRoomRegistry registry) {
        this.canvasAccessClient = canvasAccessClient;
        this.messagingTemplate = messagingTemplate;
        this.registry = registry;
    }

    private boolean checkAccessOrLog(UUID canvasId, String userId, String bearerToken, String action) {
        if (!canvasAccessClient.hasAccess(canvasId, userId, bearerToken)) {
            log.warn("Senalizacion rechazada [canvas={} user={} accion={}]: sin acceso al lienzo",
                    canvasId, userId, action);
            return false;
        }
        return true;
    }

    private boolean verifyClaimedUserId(SimpMessageHeaderAccessor headerAccessor, String claimedUserId, String action) {
        String verifiedUserId = (String) headerAccessor.getSessionAttributes().get("verifiedUserId");
        if (verifiedUserId == null || !verifiedUserId.equals(claimedUserId)) {
            log.warn("Senalizacion rechazada [accion={}]: el userId del token no coincide con el userId reclamado", action);
            return false;
        }
        return true;
    }

    @MessageMapping("/signaling/{canvasId}/join")
    public void join(@DestinationVariable UUID canvasId,
                     @Valid RoomActionCommand command,
                     SimpMessageHeaderAccessor headerAccessor) {
        if (!verifyClaimedUserId(headerAccessor, command.userId(), "join")) {
            return;
        }
        String bearerToken = (String) headerAccessor.getSessionAttributes().get("bearerToken");
        if (!checkAccessOrLog(canvasId, command.userId(), bearerToken, "join")) {
            return;
        }
        String sessionId = headerAccessor.getSessionId();
        Set<String> existingPeers = registry.addParticipant(canvasId, command.userId(), sessionId);

        messagingTemplate.convertAndSend(
                "/topic/signaling/" + canvasId + "/" + command.userId(),
                new RoomJoinedEvent(new ArrayList<>(existingPeers)));

        for (String peer : existingPeers) {
            messagingTemplate.convertAndSend(
                    "/topic/signaling/" + canvasId + "/" + peer,
                    new PeerJoinedEvent(command.userId()));
        }
    }

    @MessageMapping("/signaling/{canvasId}/leave")
    public void leave(@DestinationVariable UUID canvasId,
                      @Valid RoomActionCommand command,
                      SimpMessageHeaderAccessor headerAccessor) {
        if (!verifyClaimedUserId(headerAccessor, command.userId(), "leave")) {
            return;
        }
        String bearerToken = (String) headerAccessor.getSessionAttributes().get("bearerToken");
        checkAccessOrLog(canvasId, command.userId(), bearerToken, "leave");
        registry.removeParticipant(canvasId, command.userId());
        for (String peer : registry.getMembers(canvasId)) {
            messagingTemplate.convertAndSend(
                    "/topic/signaling/" + canvasId + "/" + peer,
                    new PeerLeftEvent(command.userId()));
        }
    }

    @MessageMapping("/signaling/{canvasId}/offer")
    public void offer(@DestinationVariable UUID canvasId,
                      @Valid SdpMessageCommand command,
                      SimpMessageHeaderAccessor headerAccessor) {
        if (!verifyClaimedUserId(headerAccessor, command.fromUserId(), "offer")) {
            return;
        }
        String bearerToken = (String) headerAccessor.getSessionAttributes().get("bearerToken");
        if (!checkAccessOrLog(canvasId, command.fromUserId(), bearerToken, "offer")) {
            return;
        }
        messagingTemplate.convertAndSend(
                "/topic/signaling/" + canvasId + "/" + command.toUserId(),
                new SdpOfferEvent(command.fromUserId(), command.sdp()));
    }

    @MessageMapping("/signaling/{canvasId}/answer")
    public void answer(@DestinationVariable UUID canvasId,
                       @Valid SdpMessageCommand command,
                       SimpMessageHeaderAccessor headerAccessor) {
        if (!verifyClaimedUserId(headerAccessor, command.fromUserId(), "answer")) {
            return;
        }
        String bearerToken = (String) headerAccessor.getSessionAttributes().get("bearerToken");
        if (!checkAccessOrLog(canvasId, command.fromUserId(), bearerToken, "answer")) {
            return;
        }
        messagingTemplate.convertAndSend(
                "/topic/signaling/" + canvasId + "/" + command.toUserId(),
                new SdpAnswerEvent(command.fromUserId(), command.sdp()));
    }

    @MessageMapping("/signaling/{canvasId}/ice-candidate")
    public void iceCandidate(@DestinationVariable UUID canvasId,
                             @Valid IceCandidateCommand command,
                             SimpMessageHeaderAccessor headerAccessor) {
        if (!verifyClaimedUserId(headerAccessor, command.fromUserId(), "ice-candidate")) {
            return;
        }
        String bearerToken = (String) headerAccessor.getSessionAttributes().get("bearerToken");
        if (!checkAccessOrLog(canvasId, command.fromUserId(), bearerToken, "ice-candidate")) {
            return;
        }
        messagingTemplate.convertAndSend(
                "/topic/signaling/" + canvasId + "/" + command.toUserId(),
                new IceCandidateEvent(command.fromUserId(), command.candidate()));
    }
}
