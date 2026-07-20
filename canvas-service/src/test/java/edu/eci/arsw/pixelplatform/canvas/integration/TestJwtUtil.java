package edu.eci.arsw.pixelplatform.canvas.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Firma tokens JWT a mano con el mismo secreto por defecto que usa
 * canvas-service (jwt.secret en application-dev.properties), replicando el
 * formato que genera auth-service (JwtService): claim "userId" + subject +
 * username. No hace falta levantar auth-service ni usar WireMock:
 * canvas-service (JwtVerifier) solo verifica la firma y lee el claim
 * "userId"; nunca llama a auth-service para validar el token.
 */
public final class TestJwtUtil {

    private static final String SECRET = "dev-secret-key-pixelplatform-auth-service-arsw-2026";

    private TestJwtUtil() {
    }

    private static SecretKey signingKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    public static String tokenFor(String userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3_600_000);
        return Jwts.builder()
                .subject(userId + "@test.com")
                .claim("username", userId)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey())
                .compact();
    }

    public static String bearerFor(String userId) {
        return "Bearer " + tokenFor(userId);
    }
}
