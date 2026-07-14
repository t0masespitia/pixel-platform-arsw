package edu.eci.arsw.pixelplatform.canvas.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateInvitationRequest(
        @NotBlank String requesterId,
        @NotBlank String targetUserId
) {
}
