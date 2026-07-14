package edu.eci.arsw.pixelplatform.canvas.dto;

import jakarta.validation.constraints.NotBlank;

public record JoinCanvasRequest(
        @NotBlank String userId,
        @NotBlank String code
) {
}
