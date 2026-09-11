package edu.eci.arsw.pixelplatform.auth.security;

import edu.eci.arsw.pixelplatform.auth.model.User;
import edu.eci.arsw.pixelplatform.auth.service.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceSecurityTest {

    private static final String TEST_SECRET =
            "pixel-platform-test-only-jwt-secret-for-automated-tests";

    private static final String COMPROMISED_SECRET =
            "dev-secret-key-pixelplatform-auth-service-arsw-2026";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3_600_000L);
        // Simula @PostConstruct
        ReflectionTestUtils.invokeMethod(jwtService, "initKey");
    }

    @Test
    void tokenFirmadoConClaveDePruebaEsValido() {
        User user = buildUser(42L, "alice", "alice@example.com");
        String token = jwtService.generateToken(user);
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractEmail(token)).isEqualTo("alice@example.com");
        assertThat(jwtService.extractUserId(token)).isEqualTo("42");
    }

    @Test
    void tokenFirmadoConClaveCompometidaEsRechazado() {
        SecretKey compromisedKey = Keys.hmacShaKeyFor(
                COMPROMISED_SECRET.getBytes(StandardCharsets.UTF_8));
        String compromisedToken = Jwts.builder()
                .subject("hacker@evil.com")
                .claim("userId", "hacker-id")
                .claim("username", "hacker")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(compromisedKey)
                .compact();

        assertThat(jwtService.isTokenValid(compromisedToken)).isFalse();
    }

    @Test
    void tokenAlteradoEsRechazado() {
        User user = buildUser(43L, "bob", "bob@example.com");
        String token = jwtService.generateToken(user);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void tokenExpiradoEsRechazado() {
        JwtService expiredService = new JwtService();
        ReflectionTestUtils.setField(expiredService, "secret", TEST_SECRET);
        ReflectionTestUtils.setField(expiredService, "expirationMs", -1L);
        ReflectionTestUtils.invokeMethod(expiredService, "initKey");

        User user = buildUser(44L, "carol", "carol@example.com");
        String expired = expiredService.generateToken(user);
        assertThat(jwtService.isTokenValid(expired)).isFalse();
    }

    @Test
    void secretoVacioLanzaExcepcionAlIniciar() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "");
        ReflectionTestUtils.setField(service, "expirationMs", 3_600_000L);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "initKey"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jwt.secret must not be blank");
    }

    @Test
    void secretoDemasiadoCortoPara256BitsLanzaExcepcionAlIniciar() {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "clave-corta");
        ReflectionTestUtils.setField(service, "expirationMs", 3_600_000L);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "initKey"))
                .isInstanceOf(io.jsonwebtoken.security.WeakKeyException.class);
    }

    // -------------------------------------------------------------------------
    // Pruebas de confusion de algoritmo
    // -------------------------------------------------------------------------

    @Test
    void tokenSinFirmaAlgNoneEsRechazado() {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"attacker@evil.com\",\"userId\":\"evil\",\"iat\":1700000000,\"exp\":9999999999}"
                        .getBytes(StandardCharsets.UTF_8));
        String unsignedToken = header + "." + payload + ".";

        assertThat(jwtService.isTokenValid(unsignedToken)).isFalse();
    }

    @Test
    void tokenConCabeceraAlgManipuladaEsRechazado() {
        User user = buildUser(99L, "victim", "victim@example.com");
        String validToken = jwtService.generateToken(user);
        String[] parts = validToken.split("\\.");
        String rs256Header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String manipulated = rs256Header + "." + parts[1] + "." + parts[2];

        assertThat(jwtService.isTokenValid(manipulated)).isFalse();
    }

    private User buildUser(long id, String username, String email) {
        User u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setEmail(email);
        return u;
    }
}
