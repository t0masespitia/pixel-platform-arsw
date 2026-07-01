package edu.eci.arsw.pixelplatform.canvas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PixelPaintCommand(
        @NotBlank String userId,
        @Min(0) int x,
        @Min(0) int y,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$") String color
) {}
