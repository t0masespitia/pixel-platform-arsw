package edu.eci.arsw.pixelplatform.canvas.integration;

import edu.eci.arsw.pixelplatform.canvas.dto.CanvasResponse;
import edu.eci.arsw.pixelplatform.canvas.dto.CreateCanvasRequest;
import edu.eci.arsw.pixelplatform.canvas.dto.CreateInvitationRequest;
import edu.eci.arsw.pixelplatform.canvas.dto.InvitationResponse;
import edu.eci.arsw.pixelplatform.canvas.dto.JoinCanvasRequest;
import edu.eci.arsw.pixelplatform.canvas.dto.LeaveCanvasRequest;
import edu.eci.arsw.pixelplatform.canvas.dto.MembershipResponse;
import edu.eci.arsw.pixelplatform.canvas.dto.MyInvitationResponse;
import edu.eci.arsw.pixelplatform.canvas.dto.RespondInvitationRequest;
import edu.eci.arsw.pixelplatform.canvas.model.CanvasConstants;
import edu.eci.arsw.pixelplatform.canvas.model.InvitationStatus;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CanvasInvitationControllerIT {

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

    private CanvasResponse crearLienzoPrivado(String ownerId) {
        HttpEntity<CreateCanvasRequest> entity = new HttpEntity<>(
                new CreateCanvasRequest("Lienzo de " + ownerId, 64, 64, ownerId), headersFor(ownerId));
        return restTemplate.postForEntity("/api/canvases", entity, CanvasResponse.class).getBody();
    }

    private InvitationResponse invitar(String canvasId, String requesterId, String targetUserId) {
        HttpEntity<CreateInvitationRequest> entity = new HttpEntity<>(
                new CreateInvitationRequest(requesterId, targetUserId), headersFor(requesterId));
        return restTemplate.postForEntity(
                "/api/canvases/" + canvasId + "/invitations", entity, InvitationResponse.class).getBody();
    }

    @Test
    void crearInvitacionEnLienzoPrivadoDeberiaDar201YQuedarPendiente() {
        CanvasResponse canvas = crearLienzoPrivado("dueno-1");

        InvitationResponse invitacion = invitar(canvas.id().toString(), "dueno-1", "invitado-1");

        assertThat(invitacion.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitacion.targetUserId()).isEqualTo("invitado-1");
        assertThat(invitacion.code()).isNotBlank();
    }

    @Test
    void crearInvitacionEnElLienzoGeneralPublicoDeberiaDar400() {
        HttpEntity<CreateInvitationRequest> entity = new HttpEntity<>(
                new CreateInvitationRequest("dueno-2", "invitado-2"), headersFor("dueno-2"));

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/canvases/" + CanvasConstants.GENERAL_CANVAS_ID + "/invitations", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void crearInvitacionSiendoNoDuenoDeberiaDar400() {
        CanvasResponse canvas = crearLienzoPrivado("dueno-3");
        HttpEntity<CreateInvitationRequest> entity = new HttpEntity<>(
                new CreateInvitationRequest("no-dueno", "invitado-3"), headersFor("no-dueno"));

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/canvases/" + canvas.id() + "/invitations", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void crearInvitacionAUnoMismoDeberiaDar400() {
        CanvasResponse canvas = crearLienzoPrivado("dueno-4");
        HttpEntity<CreateInvitationRequest> entity = new HttpEntity<>(
                new CreateInvitationRequest("dueno-4", "dueno-4"), headersFor("dueno-4"));

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/canvases/" + canvas.id() + "/invitations", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void crearInvitacionDuplicadaMientrasSigaPendienteDeberiaDar400() {
        CanvasResponse canvas = crearLienzoPrivado("dueno-5");
        invitar(canvas.id().toString(), "dueno-5", "invitado-5");

        HttpEntity<CreateInvitationRequest> entity = new HttpEntity<>(
                new CreateInvitationRequest("dueno-5", "invitado-5"), headersFor("dueno-5"));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/canvases/" + canvas.id() + "/invitations", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aceptarInvitacionDeberiaCrearMembresiaYCambiarEstado() {
        CanvasResponse canvas = crearLienzoPrivado("dueno-6");
        InvitationResponse invitacion = invitar(canvas.id().toString(), "dueno-6", "invitado-6");

        HttpEntity<RespondInvitationRequest> entity = new HttpEntity<>(
                new RespondInvitationRequest("invitado-6", true), headersFor("invitado-6"));
        ResponseEntity<InvitationResponse> response = restTemplate.postForEntity(
                "/api/canvases/" + canvas.id() + "/invitations/" + invitacion.id() + "/respond",
                entity, InvitationResponse.class);

        assertThat(response.getBody().status()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(canvasMembershipRepository.existsByCanvasIdAndUserId(canvas.id(), "invitado-6")).isTrue();
    }

    @Test
    void rechazarInvitacionDeberiaCambiarEstadoSinCrearMembresia() {
        CanvasResponse canvas = crearLienzoPrivado("dueno-7");
        InvitationResponse invitacion = invitar(canvas.id().toString(), "dueno-7", "invitado-7");

        HttpEntity<RespondInvitationRequest> entity = new HttpEntity<>(
                new RespondInvitationRequest("invitado-7", false), headersFor("invitado-7"));
        ResponseEntity<InvitationResponse> response = restTemplate.postForEntity(
                "/api/canvases/" + canvas.id() + "/invitations/" + invitacion.id() + "/respond",
                entity, InvitationResponse.class);

        assertThat(response.getBody().status()).isEqualTo(InvitationStatus.REJECTED);
        assertThat(canvasMembershipRepository.existsByCanvasIdAndUserId(canvas.id(), "invitado-7")).isFalse();
    }

    @Test
    void responderInvitacionDeOtroUsuarioDeberiaDar403() {
        CanvasResponse canvas = crearLienzoPrivado("dueno-8");
        InvitationResponse invitacion = invitar(canvas.id().toString(), "dueno-8", "invitado-8");

        HttpEntity<RespondInvitationRequest> entity = new HttpEntity<>(
                new RespondInvitationRequest("invitado-8", true), headersFor("intruso"));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/canvases/" + canvas.id() + "/invitations/" + invitacion.id() + "/respond",
                entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listInvitationsSoloElDuenoPuedeVerlas() {
        CanvasResponse canvas = crearLienzoPrivado("dueno-9");
        invitar(canvas.id().toString(), "dueno-9", "invitado-9");

        ResponseEntity<InvitationResponse[]> ok = restTemplate.exchange(
                "/api/canvases/" + canvas.id() + "/invitations?requesterId=dueno-9",
                HttpMethod.GET, new HttpEntity<>(headersFor("dueno-9")), InvitationResponse[].class);
        ResponseEntity<Map> forbidden = restTemplate.exchange(
                "/api/canvases/" + canvas.id() + "/invitations?requesterId=otro",
                HttpMethod.GET, new HttpEntity<>(headersFor("otro")), Map.class);

        assertThat(ok.getBody()).hasSize(1);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listMyInvitationsDeberiaListarSoloLasPendientesDelUsuario() {
        CanvasResponse canvasUno = crearLienzoPrivado("dueno-10");
        CanvasResponse canvasDos = crearLienzoPrivado("dueno-11");
        invitar(canvasUno.id().toString(), "dueno-10", "invitado-10");
        InvitationResponse rechazada = invitar(canvasDos.id().toString(), "dueno-11", "invitado-10");
        restTemplate.postForEntity(
                "/api/canvases/" + canvasDos.id() + "/invitations/" + rechazada.id() + "/respond",
                new HttpEntity<>(new RespondInvitationRequest("invitado-10", false), headersFor("invitado-10")),
                InvitationResponse.class);

        ResponseEntity<MyInvitationResponse[]> response = restTemplate.exchange(
                "/api/canvases/invitations/mine?userId=invitado-10",
                HttpMethod.GET, new HttpEntity<>(headersFor("invitado-10")), MyInvitationResponse[].class);

        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].canvasId()).isEqualTo(canvasUno.id());
    }

    @Test
    void joinCanvasConCodigoValidoDeberiaAgregarComoMiembro() {
        CanvasResponse canvas = crearLienzoPrivado("dueno-12");
        InvitationResponse invitacion = invitar(canvas.id().toString(), "dueno-12", "invitado-12");

        HttpEntity<JoinCanvasRequest> entity = new HttpEntity<>(
                new JoinCanvasRequest("invitado-12", invitacion.code()), headersFor("invitado-12"));
        ResponseEntity<CanvasResponse> response = restTemplate.postForEntity(
                "/api/canvases/join", entity, CanvasResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(canvasMembershipRepository.existsByCanvasIdAndUserId(canvas.id(), "invitado-12")).isTrue();
    }

    @Test
    void joinCanvasConCodigoInvalidoDeberiaDar400() {
        HttpEntity<JoinCanvasRequest> entity = new HttpEntity<>(
                new JoinCanvasRequest("cualquiera", "CODIGOMALO"), headersFor("cualquiera"));

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/canvases/join", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void joinCanvasConCodigoQueNoLePerteneceDeberiaDar400() {
        CanvasResponse canvas = crearLienzoPrivado("dueno-13");
        InvitationResponse invitacion = invitar(canvas.id().toString(), "dueno-13", "invitado-13");

        HttpEntity<JoinCanvasRequest> entity = new HttpEntity<>(
                new JoinCanvasRequest("otro-usuario", invitacion.code()), headersFor("otro-usuario"));
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/canvases/join", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listMembersSoloElDuenoPuedeVerlos() {
        CanvasResponse canvas = crearLienzoPrivado("dueno-14");
        InvitationResponse invitacion = invitar(canvas.id().toString(), "dueno-14", "invitado-14");
        restTemplate.postForEntity(
                "/api/canvases/" + canvas.id() + "/invitations/" + invitacion.id() + "/respond",
                new HttpEntity<>(new RespondInvitationRequest("invitado-14", true), headersFor("invitado-14")),
                InvitationResponse.class);

        ResponseEntity<MembershipResponse[]> ok = restTemplate.exchange(
                "/api/canvases/" + canvas.id() + "/members?requesterId=dueno-14",
                HttpMethod.GET, new HttpEntity<>(headersFor("dueno-14")), MembershipResponse[].class);

        assertThat(ok.getBody()).extracting(MembershipResponse::userId)
                .containsExactlyInAnyOrder("dueno-14", "invitado-14");
    }

    @Test
    void removeMemberDeberiaExpulsarloYElDuenoNoPuedeSerExpulsado() {
        CanvasResponse canvas = crearLienzoPrivado("dueno-15");
        InvitationResponse invitacion = invitar(canvas.id().toString(), "dueno-15", "invitado-15");
        restTemplate.postForEntity(
                "/api/canvases/" + canvas.id() + "/invitations/" + invitacion.id() + "/respond",
                new HttpEntity<>(new RespondInvitationRequest("invitado-15", true), headersFor("invitado-15")),
                InvitationResponse.class);

        HttpEntity<Void> entity = new HttpEntity<>(headersFor("dueno-15"));
        ResponseEntity<Void> expulsado = restTemplate.exchange(
                "/api/canvases/" + canvas.id() + "/members/invitado-15?requesterId=dueno-15",
                HttpMethod.DELETE, entity, Void.class);
        ResponseEntity<Map> expulsarDueno = restTemplate.exchange(
                "/api/canvases/" + canvas.id() + "/members/dueno-15?requesterId=dueno-15",
                HttpMethod.DELETE, entity, Map.class);

        assertThat(expulsado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(canvasMembershipRepository.existsByCanvasIdAndUserId(canvas.id(), "invitado-15")).isFalse();
        assertThat(expulsarDueno.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void leaveCanvasDeberiaFuncionarParaMiembrosYElDuenoNoPuedeAbandonar() {
        CanvasResponse canvas = crearLienzoPrivado("dueno-16");
        InvitationResponse invitacion = invitar(canvas.id().toString(), "dueno-16", "invitado-16");
        restTemplate.postForEntity(
                "/api/canvases/" + canvas.id() + "/invitations/" + invitacion.id() + "/respond",
                new HttpEntity<>(new RespondInvitationRequest("invitado-16", true), headersFor("invitado-16")),
                InvitationResponse.class);

        ResponseEntity<Void> salida = restTemplate.postForEntity(
                "/api/canvases/" + canvas.id() + "/leave",
                new HttpEntity<>(new LeaveCanvasRequest("invitado-16"), headersFor("invitado-16")), Void.class);
        ResponseEntity<Map> salidaDueno = restTemplate.postForEntity(
                "/api/canvases/" + canvas.id() + "/leave",
                new HttpEntity<>(new LeaveCanvasRequest("dueno-16"), headersFor("dueno-16")), Map.class);

        assertThat(salida.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(canvasMembershipRepository.existsByCanvasIdAndUserId(canvas.id(), "invitado-16")).isFalse();
        assertThat(salidaDueno.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void cancelInvitationDeberiaEliminarlaYSoloElDuenoPuedeHacerlo() {
        CanvasResponse canvas = crearLienzoPrivado("dueno-17");
        InvitationResponse invitacion = invitar(canvas.id().toString(), "dueno-17", "invitado-17");

        ResponseEntity<Map> forbidden = restTemplate.exchange(
                "/api/canvases/" + canvas.id() + "/invitations/" + invitacion.id() + "?requesterId=otro",
                HttpMethod.DELETE, new HttpEntity<>(headersFor("otro")), Map.class);
        ResponseEntity<Void> cancelada = restTemplate.exchange(
                "/api/canvases/" + canvas.id() + "/invitations/" + invitacion.id() + "?requesterId=dueno-17",
                HttpMethod.DELETE, new HttpEntity<>(headersFor("dueno-17")), Void.class);

        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(cancelada.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(canvasInvitationRepository.findById(invitacion.id())).isEmpty();
    }
}
