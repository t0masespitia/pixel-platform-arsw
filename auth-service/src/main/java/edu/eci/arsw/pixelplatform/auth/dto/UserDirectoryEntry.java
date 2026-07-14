package edu.eci.arsw.pixelplatform.auth.dto;

public record UserDirectoryEntry(
        String userId, String username, String firstName,
        String lastName, String avatarUrl
) {}
