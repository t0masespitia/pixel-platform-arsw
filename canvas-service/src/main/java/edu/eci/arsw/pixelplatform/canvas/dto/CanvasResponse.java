package edu.eci.arsw.pixelplatform.canvas.dto;

import java.time.Instant;
import java.util.UUID;

public record CanvasResponse(
        UUID id,
        String name,
        String ownerId,
        int width,
        int height,
        boolean isPrivate,
        boolean isDefaultTemplate,
        Instant createdAt
) {}
