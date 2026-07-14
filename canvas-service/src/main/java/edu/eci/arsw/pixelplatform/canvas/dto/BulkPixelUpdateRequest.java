package edu.eci.arsw.pixelplatform.canvas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record BulkPixelUpdateRequest(
        @NotBlank String requesterId,
        @NotEmpty Map<String, String> pixels
) {}
