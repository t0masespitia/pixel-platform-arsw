package edu.eci.arsw.pixelplatform.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SendInvitationEmailRequest(
        @NotBlank String targetUserId,
        @NotBlank String inviterName,
        @NotBlank String canvasName,
        @NotBlank String code,
        @NotBlank String joinLink
) {}
