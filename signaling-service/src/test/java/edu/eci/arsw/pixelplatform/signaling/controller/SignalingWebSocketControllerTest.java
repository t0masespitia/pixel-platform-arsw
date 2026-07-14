package edu.eci.arsw.pixelplatform.signaling.controller;

import edu.eci.arsw.pixelplatform.signaling.client.CanvasAccessClient;
import edu.eci.arsw.pixelplatform.signaling.dto.RoomActionCommand;
import edu.eci.arsw.pixelplatform.signaling.dto.RoomJoinedEvent;
import edu.eci.arsw.pixelplatform.signaling.dto.SdpMessageCommand;
import edu.eci.arsw.pixelplatform.signaling.dto.SdpOfferEvent;
import edu.eci.arsw.pixelplatform.signaling.registry.SignalingRoomRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SignalingWebSocketControllerTest {

    @Mock
    private CanvasAccessClient canvasAccessClient;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private SignalingRoomRegistry registry;

    @Mock
    private SimpMessageHeaderAccessor headerAccessor;

    private SignalingWebSocketController controller;

    private static final UUID CANVAS_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String BEARER_TOKEN = "Bearer test-token";

    @BeforeEach
    void setUp() {
        controller = new SignalingWebSocketController(canvasAccessClient, messagingTemplate, registry);
    }

    private void stubSessionAttributes(String userId) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("verifiedUserId", userId);
        attrs.put("bearerToken", BEARER_TOKEN);
        when(headerAccessor.getSessionAttributes()).thenReturn(attrs);
    }

    @Test
    void join_accesosDenegado_noLlamaRegistryNiMessaging() {
        stubSessionAttributes("user1");
        when(canvasAccessClient.hasAccess(CANVAS_ID, "user1", BEARER_TOKEN)).thenReturn(false);

        controller.join(CANVAS_ID, new RoomActionCommand("user1"), headerAccessor);

        verify(registry, never()).addParticipant(any(), any(), any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void join_accesoPermitido_llamaRegistryYEnviaRoomJoinedEvent() {
        stubSessionAttributes("user1");
        when(canvasAccessClient.hasAccess(CANVAS_ID, "user1", BEARER_TOKEN)).thenReturn(true);
        when(headerAccessor.getSessionId()).thenReturn("session1");
        when(registry.addParticipant(CANVAS_ID, "user1", "session1")).thenReturn(Set.of());

        controller.join(CANVAS_ID, new RoomActionCommand("user1"), headerAccessor);

        verify(registry).addParticipant(CANVAS_ID, "user1", "session1");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/signaling/" + CANVAS_ID + "/user1"),
                payloadCaptor.capture());
        assertInstanceOf(RoomJoinedEvent.class, payloadCaptor.getValue());
    }

    @Test
    void offer_accesosDenegado_noRelayaMensaje() {
        stubSessionAttributes("user1");
        when(canvasAccessClient.hasAccess(CANVAS_ID, "user1", BEARER_TOKEN)).thenReturn(false);

        controller.offer(CANVAS_ID, new SdpMessageCommand("user1", "user2", "sdp-data"), headerAccessor);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void offer_accesoPermitido_relayaSdpOfferEventAlToUserId() {
        stubSessionAttributes("user1");
        when(canvasAccessClient.hasAccess(CANVAS_ID, "user1", BEARER_TOKEN)).thenReturn(true);

        controller.offer(CANVAS_ID, new SdpMessageCommand("user1", "user2", "sdp-data"), headerAccessor);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/signaling/" + CANVAS_ID + "/user2"),
                payloadCaptor.capture());
        SdpOfferEvent event = assertInstanceOf(SdpOfferEvent.class, payloadCaptor.getValue());
        assertEquals("user1", event.fromUserId());
        assertEquals("sdp-data", event.sdp());
    }
}
