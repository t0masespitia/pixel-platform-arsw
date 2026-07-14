package edu.eci.arsw.pixelplatform.canvas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RespondInvitationRequest(
        @NotBlank String userId,
        @NotNull Boolean accept
) {}
