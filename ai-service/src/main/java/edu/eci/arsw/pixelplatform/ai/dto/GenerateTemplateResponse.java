package edu.eci.arsw.pixelplatform.ai.dto;

import java.util.UUID;

public record GenerateTemplateResponse(UUID canvasId, int pixelsWritten, int width, int height) {}
