package edu.eci.arsw.pixelplatform.chat.service;

import edu.eci.arsw.pixelplatform.chat.dto.DirectMessageResponse;
import edu.eci.arsw.pixelplatform.chat.dto.SendDirectMessageRequest;
import edu.eci.arsw.pixelplatform.chat.model.DirectMessage;
import edu.eci.arsw.pixelplatform.chat.repository.DirectMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceTest {

    @Mock
    private DirectMessageRepository directMessageRepository;

    private DirectMessageService service;

    private static final String USER_A = "1";
    private static final String USER_B = "2";

    @BeforeEach
    void setUp() {
        service = new DirectMessageService(directMessageRepository);
    }

    @Test
    void sendMessage_guardaCorrectamenteYRetornaResponse() {
        SendDirectMessageRequest request = new SendDirectMessageRequest(USER_B, "hola");
        ArgumentCaptor<DirectMessage> captor = ArgumentCaptor.forClass(DirectMessage.class);

        when(directMessageRepository.save(any(DirectMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        DirectMessageResponse response = service.sendMessage(USER_A, request);

        verify(directMessageRepository).save(captor.capture());
        DirectMessage saved = captor.getValue();

        assertNotNull(saved.getId());
        assertEquals(USER_A, saved.getFromUserId());
        assertEquals(USER_B, saved.getToUserId());
        assertEquals("hola", saved.getContent());
        assertNotNull(saved.getSentAt());

        assertEquals(USER_A, response.fromUserId());
        assertEquals(USER_B, response.toUserId());
        assertEquals("hola", response.content());
    }

    @Test
    void getConversation_llamaFindConversationConAmbosUserIds() {
        DirectMessage msg = DirectMessage.builder()
                .id(UUID.randomUUID())
                .fromUserId(USER_A)
                .toUserId(USER_B)
                .content("hola")
                .sentAt(Instant.now())
                .build();
        when(directMessageRepository.findConversation(USER_A, USER_B)).thenReturn(List.of(msg));

        List<DirectMessageResponse> result = service.getConversation(USER_A, USER_B);

        verify(directMessageRepository).findConversation(USER_A, USER_B);
        assertEquals(1, result.size());
        assertEquals(USER_A, result.get(0).fromUserId());
        assertEquals(USER_B, result.get(0).toUserId());
        assertEquals("hola", result.get(0).content());
    }

    @Test
    void getConversation_esEspejoBidireccional() {
        when(directMessageRepository.findConversation(USER_B, USER_A)).thenReturn(List.of());

        service.getConversation(USER_B, USER_A);

        verify(directMessageRepository).findConversation(USER_B, USER_A);
    }

    @Test
    void deleteInvitationMessages_borraMensajesSiUsuarioParticipa() {
        UUID invitationId = UUID.randomUUID();
        DirectMessage msg = DirectMessage.builder()
                .id(UUID.randomUUID())
                .fromUserId(USER_A)
                .toUserId(USER_B)
                .content("invitacion")
                .sentAt(Instant.now())
                .invitationId(invitationId)
                .build();
        when(directMessageRepository.findByInvitationId(invitationId)).thenReturn(List.of(msg));

        service.deleteInvitationMessages(USER_B, invitationId);

        verify(directMessageRepository).deleteAll(List.of(msg));
    }

    @Test
    void deleteInvitationMessages_rechazaUsuarioAjeno() {
        UUID invitationId = UUID.randomUUID();
        DirectMessage msg = DirectMessage.builder()
                .id(UUID.randomUUID())
                .fromUserId(USER_A)
                .toUserId(USER_B)
                .content("invitacion")
                .sentAt(Instant.now())
                .invitationId(invitationId)
                .build();
        when(directMessageRepository.findByInvitationId(invitationId)).thenReturn(List.of(msg));

        assertThrows(IllegalArgumentException.class, () ->
                service.deleteInvitationMessages("3", invitationId));

        verify(directMessageRepository, never()).deleteAll(any());
    }
}
