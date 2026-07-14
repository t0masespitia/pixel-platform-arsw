package edu.eci.arsw.pixelplatform.auth.dto;

public record UserSummaryResponse(
        String userId, String username, String firstName, String lastName,
        String avatarUrl, String nickname
) {}
