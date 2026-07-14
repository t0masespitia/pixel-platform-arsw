package edu.eci.arsw.pixelplatform.canvas.dto;

import jakarta.validation.constraints.NotBlank;

public record LeaveCanvasRequest(@NotBlank String userId) {}
