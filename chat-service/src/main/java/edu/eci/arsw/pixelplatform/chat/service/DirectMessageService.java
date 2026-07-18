package edu.eci.arsw.pixelplatform.chat.service;

import edu.eci.arsw.pixelplatform.chat.dto.DirectMessageResponse;
import edu.eci.arsw.pixelplatform.chat.dto.SendCanvasInvitationMessageRequest;
import edu.eci.arsw.pixelplatform.chat.dto.SendDirectMessageRequest;
import edu.eci.arsw.pixelplatform.chat.model.DirectMessage;
import edu.eci.arsw.pixelplatform.chat.model.DirectMessageType;
import edu.eci.arsw.pixelplatform.chat.repository.DirectMessageRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DirectMessageService {

    private final DirectMessageRepository directMessageRepository;

    public DirectMessageService(DirectMessageRepository directMessageRepository) {
        this.directMessageRepository = directMessageRepository;
    }

    public DirectMessageResponse sendMessage(String fromUserId, SendDirectMessageRequest request) {
        DirectMessage msg = DirectMessage.builder()
                .id(UUID.randomUUID())
                .fromUserId(fromUserId)
                .toUserId(request.toUserId())
                .content(request.content())
                .sentAt(Instant.now())
                .messageType(DirectMessageType.TEXT)
                .build();
        DirectMessage saved = directMessageRepository.save(msg);
        return toResponse(saved);
    }

    public DirectMessageResponse sendCanvasInvitationMessage(String fromUserId,
            SendCanvasInvitationMessageRequest request) {
        DirectMessage msg = DirectMessage.builder()
                .id(UUID.randomUUID())
                .fromUserId(fromUserId)
                .toUserId(request.toUserId())
                .content(request.content())
                .sentAt(Instant.now())
                .messageType(DirectMessageType.CANVAS_INVITATION)
                .invitationId(request.invitationId())
                .canvasId(request.canvasId())
                .canvasName(request.canvasName())
                .build();
        DirectMessage saved = directMessageRepository.save(msg);
        return toResponse(saved);
    }

    public List<DirectMessageResponse> getConversation(String userId, String otherUserId) {
        return directMessageRepository.findConversation(userId, otherUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public void deleteInvitationMessages(String requesterId, UUID invitationId) {
        List<DirectMessage> messages = directMessageRepository.findByInvitationId(invitationId);
        boolean authorized = messages.stream()
                .allMatch(msg -> requesterId.equals(msg.getFromUserId()) || requesterId.equals(msg.getToUserId()));
        if (!authorized) {
            throw new IllegalArgumentException("No autorizado para eliminar esta invitacion");
        }
        directMessageRepository.deleteAll(messages);
    }

    private DirectMessageResponse toResponse(DirectMessage msg) {
        return new DirectMessageResponse(
                msg.getId(),
                msg.getFromUserId(),
                msg.getToUserId(),
                msg.getContent(),
                msg.getSentAt(),
                msg.getMessageType(),
                msg.getInvitationId(),
                msg.getCanvasId(),
                msg.getCanvasName()
        );
    }
}
