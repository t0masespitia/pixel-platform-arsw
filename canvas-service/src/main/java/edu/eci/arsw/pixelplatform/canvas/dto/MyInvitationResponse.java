package edu.eci.arsw.pixelplatform.canvas.dto;

import java.time.Instant;
import java.util.UUID;

public record MyInvitationResponse(
        UUID id, UUID canvasId, String canvasName, String invitedByUserId,
        String code, Instant createdAt
) {}
