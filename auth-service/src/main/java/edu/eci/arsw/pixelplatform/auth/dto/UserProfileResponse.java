package edu.eci.arsw.pixelplatform.auth.dto;

public record UserProfileResponse(
        String userId, String username, String firstName, String lastName,
        String avatarUrl, String nickname
) {}
