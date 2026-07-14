package edu.eci.arsw.pixelplatform.signaling.listener;

import edu.eci.arsw.pixelplatform.signaling.dto.PeerLeftEvent;
import edu.eci.arsw.pixelplatform.signaling.model.RoomParticipant;
import edu.eci.arsw.pixelplatform.signaling.registry.SignalingRoomRegistry;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Optional;

@Component
public class SignalingDisconnectListener implements ApplicationListener<SessionDisconnectEvent> {

    private final SignalingRoomRegistry registry;
    private final SimpMessagingTemplate messagingTemplate;

    public SignalingDisconnectListener(SignalingRoomRegistry registry,
                                       SimpMessagingTemplate messagingTemplate) {
        this.registry = registry;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        Optional<RoomParticipant> removed = registry.removeBySessionId(sessionId);
        removed.ifPresent(participant -> {
            for (String peer : registry.getMembers(participant.canvasId())) {
                messagingTemplate.convertAndSend(
                        "/topic/signaling/" + participant.canvasId() + "/" + peer,
                        new PeerLeftEvent(participant.userId()));
            }
        });
    }
}
