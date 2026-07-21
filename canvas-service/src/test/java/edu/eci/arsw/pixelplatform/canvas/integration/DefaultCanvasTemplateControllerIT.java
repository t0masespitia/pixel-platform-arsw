package edu.eci.arsw.pixelplatform.canvas.integration;

import edu.eci.arsw.pixelplatform.canvas.dto.CanvasResponse;
import edu.eci.arsw.pixelplatform.canvas.dto.CanvasStateDTO;
import edu.eci.arsw.pixelplatform.canvas.dto.CreateDefaultCanvasesRequest;
import edu.eci.arsw.pixelplatform.canvas.model.CanvasConstants;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasInvitationRepository;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasMembershipRepository;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DefaultCanvasTemplateControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CanvasRepository canvasRepository;

    @Autowired
    private CanvasMembershipRepository canvasMembershipRepository;

    @Autowired
    private CanvasInvitationRepository canvasInvitationRepository;

    @BeforeEach
    void limpiarDatosDePrueba() {
        canvasInvitationRepository.deleteAll();
        canvasMembershipRepository.deleteAll();
        canvasRepository.findAll().stream()
                .filter(c -> !c.getId().equals(CanvasConstants.GENERAL_CANVAS_ID))
                .forEach(canvasRepository::delete);
    }

    private HttpHeaders headersFor(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", TestJwtUtil.bearerFor(userId));
        return headers;
    }

    private ResponseEntity<CanvasResponse[]> crearDefaultTemplates(String ownerId, String tokenUserId) {
        return restTemplate.postForEntity(
                "/api/canvases/default-templates",
                new HttpEntity<>(new CreateDefaultCanvasesRequest(ownerId), headersFor(tokenUserId)),
                CanvasResponse[].class);
    }

    @Test
    void crearLienzosPredeterminadosDeberiaCrearCuatroConMembresiaYPixeles() {
        ResponseEntity<CanvasResponse[]> response = crearDefaultTemplates("user-x", "user-x");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).hasSize(4);
        assertThat(response.getBody()).allSatisfy(canvas -> {
            assertThat(canvas.isDefaultTemplate()).isTrue();
            assertThat(canvas.isPrivate()).isTrue();
            assertThat(canvasMembershipRepository.existsByCanvasIdAndUserId(canvas.id(), "user-x")).isTrue();
        });

        CanvasResponse primero = response.getBody()[0];
        ResponseEntity<CanvasStateDTO> estado = restTemplate.exchange(
                "/api/canvas/" + primero.id() + "/state", HttpMethod.GET,
                new HttpEntity<>(headersFor("user-x")), CanvasStateDTO.class);

        assertThat(estado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(estado.getBody().pixels()).isNotEmpty();
    }

    @Test
    void llamarDosVecesNoDeberiaDuplicarLienzosPredeterminados() {
        crearDefaultTemplates("user-y", "user-y");

        ResponseEntity<CanvasResponse[]> segunda = crearDefaultTemplates("user-y", "user-y");

        assertThat(segunda.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(segunda.getBody()).hasSize(4);
        long defaults = canvasRepository.findByOwnerId("user-y").stream()
                .filter(c -> c.isDefaultTemplate())
                .count();
        assertThat(defaults).isEqualTo(4);
    }

    @Test
    void tokenDeOtroUsuarioDeberiaDar403() {
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/canvases/default-templates",
                new HttpEntity<>(new CreateDefaultCanvasesRequest("user-z"), headersFor("intruso")),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deleteCanvasPredeterminadoDeberiaDar400YConservarlo() {
        CanvasResponse canvas = Arrays.stream(crearDefaultTemplates("user-delete", "user-delete").getBody())
                .findFirst()
                .orElseThrow();

        ResponseEntity<String> delete = restTemplate.exchange(
                "/api/canvases/" + canvas.id(), HttpMethod.DELETE,
                new HttpEntity<>(headersFor("user-delete")), String.class);

        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(canvasRepository.findById(canvas.id())).isPresent();
    }
}
