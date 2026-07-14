package edu.eci.arsw.pixelplatform.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SendCanvasInvitationMessageRequest(
        @NotBlank String toUserId,
        @NotNull UUID canvasId,
        @NotBlank String canvasName,
        @NotNull UUID invitationId,
        @NotBlank String content
) {}
