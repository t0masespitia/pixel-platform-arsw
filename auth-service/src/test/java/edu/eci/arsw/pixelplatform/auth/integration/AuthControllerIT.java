package edu.eci.arsw.pixelplatform.auth.integration;

import edu.eci.arsw.pixelplatform.auth.dto.LoginRequest;
import edu.eci.arsw.pixelplatform.auth.dto.RegisterRequest;
import edu.eci.arsw.pixelplatform.auth.dto.VerifyEmailRequest;
import edu.eci.arsw.pixelplatform.auth.model.User;
import edu.eci.arsw.pixelplatform.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.health.mail.enabled=false")
@Testcontainers
class AuthControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void limpiarUsuarios() {
        userRepository.deleteAll();
    }

    private RegisterRequest nuevoRegistro(String email) {
        return new RegisterRequest("Tomas", "Espitia", email, "ClaveSegura123", "ClaveSegura123", null);
    }

    @Test
    void registroExitosoDeberiaCrear201YPersistirUsuarioNoVerificado() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/register", nuevoRegistro("nuevo@test.com"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(userRepository.existsByEmail("nuevo@test.com")).isTrue();
        assertThat(userRepository.findByEmail("nuevo@test.com").get().isEmailVerified()).isFalse();
    }

    @Test
    void registroConEmailDuplicadoDeberiaDar400() {
        restTemplate.postForEntity("/api/auth/register", nuevoRegistro("dup@test.com"), Map.class);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/register", nuevoRegistro("dup@test.com"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void loginAntesDeVerificarEmailDeberiaDar403() {
        restTemplate.postForEntity("/api/auth/register", nuevoRegistro("sinverificar@test.com"), Map.class);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequest("sinverificar@test.com", "ClaveSegura123"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("emailVerified", false);
    }

    @Test
    void flujoCompletoRegistroVerificacionYLoginDeberiaFuncionar() throws Exception {
        restTemplate.postForEntity("/api/auth/register", nuevoRegistro("completo@test.com"), Map.class);
        User user = userRepository.findByEmail("completo@test.com").orElseThrow();
        String codigoReal = user.getVerificationCode();

        ResponseEntity<Map> verifyResponse = restTemplate.postForEntity(
                "/api/auth/verify-email",
                new VerifyEmailRequest("completo@test.com", codigoReal),
                Map.class);
        assertThat(verifyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> loginOk = restTemplate.postForEntity(
                "/api/auth/login",
                new LoginRequest("completo@test.com", "ClaveSegura123"),
                Map.class);
        assertThat(loginOk.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginOk.getBody()).containsKey("token");

        assertThat(postLoginStatus("completo@test.com", "ClaveIncorrecta"))
                .isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void verificarConCodigoInvalidoDeberiaDar400() {
        restTemplate.postForEntity("/api/auth/register", nuevoRegistro("codigomalo@test.com"), Map.class);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/auth/verify-email",
                new VerifyEmailRequest("codigomalo@test.com", "000000"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void endpointProtegidoSinTokenDeberiaDar401() {
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/users", HttpMethod.GET, entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private int postLoginStatus(String email, String password) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }
}
