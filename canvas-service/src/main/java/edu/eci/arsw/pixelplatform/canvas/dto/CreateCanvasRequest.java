package edu.eci.arsw.pixelplatform.canvas.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateCanvasRequest(
        @NotBlank String name,
        @Min(32) @Max(500) int width,
        @Min(32) @Max(500) int height,
        @NotBlank String ownerId
) {}
