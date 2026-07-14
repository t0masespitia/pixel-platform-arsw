package edu.eci.arsw.pixelplatform.chat.dto;

import java.time.Instant;

public record ChatMessageResponse(String userId, String message, Instant timestamp) {
}
