package edu.eci.arsw.pixelplatform.canvas.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtVerifier {

    private final String secret;

    public JwtVerifier(@Value("${jwt.secret}") String secret) {
        this.secret = secret;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String verifyAndExtractUserId(String bearerHeaderValue) {
        if (bearerHeaderValue == null || !bearerHeaderValue.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Falta el token de autenticacion");
        }
        String token = bearerHeaderValue.substring(7);
        try {
            var claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return String.valueOf(claims.get("userId"));
        } catch (JwtException e) {
            throw new IllegalArgumentException("Token invalido o expirado");
        }
    }
}
