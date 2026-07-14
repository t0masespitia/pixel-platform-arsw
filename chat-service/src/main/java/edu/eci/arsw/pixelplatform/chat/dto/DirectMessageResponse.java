package edu.eci.arsw.pixelplatform.chat.dto;

import edu.eci.arsw.pixelplatform.chat.model.DirectMessageType;

import java.time.Instant;
import java.util.UUID;

public record DirectMessageResponse(
        UUID id, String fromUserId, String toUserId, String content,
        Instant sentAt, DirectMessageType messageType,
        UUID invitationId, UUID canvasId, String canvasName
) {}
