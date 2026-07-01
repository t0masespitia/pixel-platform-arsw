package edu.eci.arsw.pixelplatform.auth.dto;

public record AuthResponse(
        String token,
        String username,
        String email,
        long expiresIn
) {}
