package edu.eci.arsw.pixelplatform.canvas.dto;

import java.util.Map;

public record CanvasStateDTO(
        Map<String, String> pixels
) {}
