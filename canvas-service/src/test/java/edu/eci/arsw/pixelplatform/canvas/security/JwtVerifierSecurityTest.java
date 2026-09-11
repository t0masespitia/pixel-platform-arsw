package edu.eci.arsw.pixelplatform.canvas.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtVerifierSecurityTest {

    private static final String TEST_SECRET =
            "pixel-platform-test-only-jwt-secret-for-automated-tests";

    private static final String COMPROMISED_SECRET =
            "dev-secret-key-pixelplatform-auth-service-arsw-2026";

    private JwtVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new JwtVerifier(TEST_SECRET);
    }

    @Test
    void tokenFirmadoConClaveDePruebaEsAceptado() {
        String bearer = buildBearer(TEST_SECRET, "user-test-1", 3_600_000L);
        assertThat(verifier.verifyAndExtractUserId(bearer)).isEqualTo("user-test-1");
    }

    @Test
    void tokenFirmadoConClaveCompometidaEsRechazado() {
        String bearer = buildBearer(COMPROMISED_SECRET, "hacker", 3_600_000L);
        assertThatThrownBy(() -> verifier.verifyAndExtractUserId(bearer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token invalido o expirado");
    }

    @Test
    void tokenAlteradoEsRechazado() {
        String bearer = buildBearer(TEST_SECRET, "user-test-2", 3_600_000L);
        String tampered = bearer.substring(0, bearer.length() - 5) + "XXXXX";
        assertThatThrownBy(() -> verifier.verifyAndExtractUserId(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token invalido o expirado");
    }

    @Test
    void tokenExpiradoEsRechazado() {
        String bearer = buildBearer(TEST_SECRET, "user-test-3", -1L);
        assertThatThrownBy(() -> verifier.verifyAndExtractUserId(bearer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token invalido o expirado");
    }

    @Test
    void bearerAusenteOSinPrefixEsRechazado() {
        assertThatThrownBy(() -> verifier.verifyAndExtractUserId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Falta el token de autenticacion");

        assertThatThrownBy(() -> verifier.verifyAndExtractUserId("sinespacioBearer token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Falta el token de autenticacion");
    }

    @Test
    void secretoVacioLanzaExcepcionAlInstanciar() {
        assertThatThrownBy(() -> new JwtVerifier(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret must not be blank");
    }

    @Test
    void secretoDemasiadoCortoPara256BitsLanzaExcepcionAlInstanciar() {
        assertThatThrownBy(() -> new JwtVerifier("clave-corta"))
                .isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class);
    }

    // -------------------------------------------------------------------------
    // Pruebas de confusion de algoritmo
    // -------------------------------------------------------------------------

    @Test
    void tokenSinFirmaAlgNoneEsRechazado() {
        // JWT sin firma: header.payload. (signature vacia)
        // alg:none no es un JWS valido; parseSignedClaims() debe rechazarlo.
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"attacker@evil.com\",\"userId\":\"evil\",\"iat\":1700000000,\"exp\":9999999999}"
                        .getBytes(StandardCharsets.UTF_8));
        String unsignedToken = "Bearer " + header + "." + payload + ".";

        assertThatThrownBy(() -> verifier.verifyAndExtractUserId(unsignedToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token invalido o expirado");
    }

    @Test
    void tokenConCabeceraAlgManipuladaEsRechazado() {
        // Token HS256 valido → se reemplaza la cabecera por una que dice RS256.
        // La firma ya no cuadra con el nuevo header, por lo que debe ser rechazado.
        String validBearer = buildBearer(TEST_SECRET, "user-alg-test", 3_600_000L);
        String[] parts = validBearer.substring(7).split("\\.");
        // Reemplazamos solo el header por uno que declara RS256
        String rs256Header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String manipulated = "Bearer " + rs256Header + "." + parts[1] + "." + parts[2];

        assertThatThrownBy(() -> verifier.verifyAndExtractUserId(manipulated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token invalido o expirado");
    }

    private String buildBearer(String secret, String userId, long ttlMs) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String token = Jwts.builder()
                .subject(userId + "@test.local")
                .claim("username", userId)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
        return "Bearer " + token;
    }
}
