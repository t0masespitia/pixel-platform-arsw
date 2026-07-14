package edu.eci.arsw.pixelplatform.canvas.dto;

import java.time.Instant;

public record MembershipResponse(String userId, Instant joinedAt) {
}
