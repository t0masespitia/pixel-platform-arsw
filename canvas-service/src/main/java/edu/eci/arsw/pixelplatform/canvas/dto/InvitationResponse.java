package edu.eci.arsw.pixelplatform.canvas.dto;

import edu.eci.arsw.pixelplatform.canvas.model.InvitationStatus;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
        UUID id, UUID canvasId, String targetUserId, String invitedByUserId,
        String code, String joinLink, InvitationStatus status, Instant createdAt
) {
}
