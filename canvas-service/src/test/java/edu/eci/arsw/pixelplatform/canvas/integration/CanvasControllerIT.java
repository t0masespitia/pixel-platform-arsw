package edu.eci.arsw.pixelplatform.canvas.integration;

import edu.eci.arsw.pixelplatform.canvas.dto.BulkPixelUpdateRequest;
import edu.eci.arsw.pixelplatform.canvas.dto.CanvasResponse;
import edu.eci.arsw.pixelplatform.canvas.dto.CanvasStateDTO;
import edu.eci.arsw.pixelplatform.canvas.dto.CreateCanvasRequest;
import edu.eci.arsw.pixelplatform.canvas.dto.PixelDTO;
import edu.eci.arsw.pixelplatform.canvas.model.CanvasConstants;
import edu.eci.arsw.pixelplatform.canvas.model.CanvasMembership;
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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CanvasControllerIT {

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
    void limpiarLienzosDePrueba() {
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

    private CanvasResponse crearLienzo(String ownerId) {
        HttpEntity<CreateCanvasRequest> entity = new HttpEntity<>(
                new CreateCanvasRequest("Lienzo de " + ownerId, 64, 64, ownerId), headersFor(ownerId));
        return restTemplate.postForEntity("/api/canvases", entity, CanvasResponse.class).getBody();
    }

    @Test
    void crearLienzoDeberiaDar201YCrearMembresiaDelDueno() {
        HttpEntity<CreateCanvasRequest> entity = new HttpEntity<>(
                new CreateCanvasRequest("Mi lienzo", 64, 64, "owner-1"), headersFor("owner-1"));

        ResponseEntity<CanvasResponse> response = restTemplate.postForEntity(
                "/api/canvases", entity, CanvasResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().ownerId()).isEqualTo("owner-1");
        assertThat(canvasMembershipRepository.existsByCanvasIdAndUserId(response.getBody().id(), "owner-1")).isTrue();
    }

    @Test
    void crearLienzoConTokenDeOtroUsuarioDeberiaDar403() {
        HttpEntity<CreateCanvasRequest> entity = new HttpEntity<>(
                new CreateCanvasRequest("Lienzo ajeno", 64, 64, "owner-1"), headersFor("impostor"));

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/canvases", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getCanvasDeberiaEncontrarLienzoExistenteYDar404SiNoExiste() {
        CanvasResponse creado = crearLienzo("owner-2");
        HttpEntity<Void> entity = new HttpEntity<>(headersFor("owner-2"));

        ResponseEntity<CanvasResponse> ok = restTemplate.exchange(
                "/api/canvases/" + creado.id(), HttpMethod.GET, entity, CanvasResponse.class);
        ResponseEntity<Map> notFound = restTemplate.exchange(
                "/api/canvases/" + UUID.randomUUID(), HttpMethod.GET, entity, Map.class);

        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getGeneralCanvasDeberiaEstarSembradoDesdeElArranque() {
        HttpEntity<Void> entity = new HttpEntity<>(headersFor("cualquiera"));

        ResponseEntity<CanvasResponse> response = restTemplate.exchange(
                "/api/canvases/general", HttpMethod.GET, entity, CanvasResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(CanvasConstants.GENERAL_CANVAS_ID);
        assertThat(response.getBody().isPrivate()).isFalse();
    }

    @Test
    void getCanvasesByOwnerDeberiaFiltrarPorDuenoYExigirTokenPropio() {
        crearLienzo("owner-3");
        crearLienzo("owner-3");
        crearLienzo("owner-4");

        HttpEntity<Void> entity = new HttpEntity<>(headersFor("owner-3"));
        ResponseEntity<CanvasResponse[]> ok = restTemplate.exchange(
                "/api/canvases?ownerId=owner-3", HttpMethod.GET, entity, CanvasResponse[].class);
        ResponseEntity<Map> forbidden = restTemplate.exchange(
                "/api/canvases?ownerId=owner-4", HttpMethod.GET, entity, Map.class);

        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok.getBody()).hasSize(2);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void getSharedCanvasesDeberiaListarSoloLosLienzosDondeEsMiembroPeroNoDueno() {
        CanvasResponse ajeno = crearLienzo("owner-5");
        canvasMembershipRepository.save(CanvasMembership.builder()
                .id(UUID.randomUUID())
                .canvasId(ajeno.id())
                .userId("miembro-5")
                .joinedAt(Instant.now())
                .build());

        HttpEntity<Void> entity = new HttpEntity<>(headersFor("miembro-5"));
        ResponseEntity<CanvasResponse[]> response = restTemplate.exchange(
                "/api/canvases/shared?userId=miembro-5", HttpMethod.GET, entity, CanvasResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(CanvasResponse::id).containsExactly(ajeno.id());
    }

    @Test
    void checkAccessDeberiaDistinguirMiembrosDeNoMiembros() {
        CanvasResponse canvas = crearLienzo("owner-6");

        ResponseEntity<Map> accesoDueno = restTemplate.exchange(
                "/api/canvases/" + canvas.id() + "/access?userId=owner-6",
                HttpMethod.GET, new HttpEntity<>(headersFor("owner-6")), Map.class);
        ResponseEntity<Map> accesoExtrano = restTemplate.exchange(
                "/api/canvases/" + canvas.id() + "/access?userId=extrano",
                HttpMethod.GET, new HttpEntity<>(headersFor("extrano")), Map.class);

        assertThat(accesoDueno.getBody()).containsEntry("allowed", true);
        assertThat(accesoExtrano.getBody()).containsEntry("allowed", false);
    }

    @Test
    void deleteCanvasDeberiaEliminarloYDejarDeEncontrarseDespues() {
        CanvasResponse canvas = crearLienzo("owner-7");
        HttpEntity<Void> entity = new HttpEntity<>(headersFor("owner-7"));

        ResponseEntity<Void> delete = restTemplate.exchange(
                "/api/canvases/" + canvas.id(), HttpMethod.DELETE, entity, Void.class);
        ResponseEntity<Map> getDespues = restTemplate.exchange(
                "/api/canvases/" + canvas.id(), HttpMethod.GET, entity, Map.class);

        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(getDespues.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteCanvasPorUnUsuarioQueNoEsElDuenoDeberiaDar403() {
        CanvasResponse canvas = crearLienzo("owner-8");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/canvases/" + canvas.id(), HttpMethod.DELETE,
                new HttpEntity<>(headersFor("impostor")), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deleteCanvasGeneralDeberiaEstarProhibido() {
        // El lienzo general tiene ownerId null, asi que el chequeo de dueno del
        // controller (canvas.ownerId() == null -> 403) bloquea el borrado antes
        // de llegar a la regla de negocio explicita en el servicio.
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/canvases/" + CanvasConstants.GENERAL_CANVAS_ID, HttpMethod.DELETE,
                new HttpEntity<>(headersFor("cualquiera")), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void endpointProtegidoSinTokenDeberiaDar401() {
        // GET sin body: si se hace con POST+body, el filtro corta la respuesta
        // antes de que el cliente termine de mandar el body y Tomcat puede
        // resetear la conexion (ResourceAccessException) en vez de dar un 401
        // limpio. Con GET no hay ese problema.
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/canvases/general", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void pintarUnPixelYLuegoConsultarElEstadoDeberiaReflejarlo() {
        CanvasResponse canvas = crearLienzo("owner-10");
        PixelDTO pixel = new PixelDTO(3, 4, "#FF00FF");
        HttpEntity<PixelDTO> pintarEntity = new HttpEntity<>(pixel, headersFor("owner-10"));

        ResponseEntity<Map> pintado = restTemplate.postForEntity(
                "/api/canvas/" + canvas.id() + "/pixel", pintarEntity, Map.class);
        ResponseEntity<CanvasStateDTO> estado = restTemplate.exchange(
                "/api/canvas/" + canvas.id() + "/state", HttpMethod.GET,
                new HttpEntity<>(headersFor("owner-10")), CanvasStateDTO.class);

        assertThat(pintado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(estado.getBody().pixels()).containsEntry("3,4", "#FF00FF");
    }

    @Test
    void bulkSetPixelsConRequesterIdDistintoAlTokenDeberiaDar403() {
        CanvasResponse canvas = crearLienzo("owner-11");
        BulkPixelUpdateRequest request = new BulkPixelUpdateRequest("owner-11", Map.of("0,0", "#000000"));
        HttpEntity<BulkPixelUpdateRequest> entity = new HttpEntity<>(request, headersFor("impostor"));

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/canvas/" + canvas.id() + "/pixels/bulk", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
