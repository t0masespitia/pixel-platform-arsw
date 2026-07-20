package edu.eci.arsw.pixelplatform.chat.integration;

import edu.eci.arsw.pixelplatform.chat.dto.DirectMessageResponse;
import edu.eci.arsw.pixelplatform.chat.dto.SendCanvasInvitationMessageRequest;
import edu.eci.arsw.pixelplatform.chat.dto.SendDirectMessageRequest;
import edu.eci.arsw.pixelplatform.chat.model.DirectMessageType;
import edu.eci.arsw.pixelplatform.chat.repository.DirectMessageRepository;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DirectMessageControllerIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DirectMessageRepository directMessageRepository;

    @BeforeEach
    void limpiarMensajesDePrueba() {
        directMessageRepository.deleteAll();
    }

    private HttpHeaders headersFor(String userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", TestJwtUtil.bearerFor(userId));
        return headers;
    }

    @Test
    void enviarMensajeDeberiaDar201YUsarElUsuarioDelTokenComoRemitente() {
        HttpEntity<SendDirectMessageRequest> entity = new HttpEntity<>(
                new SendDirectMessageRequest("usuario-b", "hola!"), headersFor("usuario-a"));

        ResponseEntity<DirectMessageResponse> response = restTemplate.postForEntity(
                "/api/messages", entity, DirectMessageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().fromUserId()).isEqualTo("usuario-a");
        assertThat(response.getBody().toUserId()).isEqualTo("usuario-b");
        assertThat(response.getBody().messageType()).isEqualTo(DirectMessageType.TEXT);
    }

    @Test
    void enviarMensajeConContenidoVacioDeberiaDar400() {
        HttpEntity<SendDirectMessageRequest> entity = new HttpEntity<>(
                new SendDirectMessageRequest("usuario-b", ""), headersFor("usuario-a"));

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/messages", entity, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void enviarInvitacionDeCanvasDeberiaDar201ConMetadatosDeLaInvitacion() {
        UUID canvasId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        HttpEntity<SendCanvasInvitationMessageRequest> entity = new HttpEntity<>(
                new SendCanvasInvitationMessageRequest(
                        "invitado-1", canvasId, "Mi lienzo", invitationId, "te invito a mi lienzo"),
                headersFor("dueno-1"));

        ResponseEntity<DirectMessageResponse> response = restTemplate.postForEntity(
                "/api/messages/canvas-invitation", entity, DirectMessageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().messageType()).isEqualTo(DirectMessageType.CANVAS_INVITATION);
        assertThat(response.getBody().canvasId()).isEqualTo(canvasId);
        assertThat(response.getBody().invitationId()).isEqualTo(invitationId);
    }

    @Test
    void getConversationDeberiaDevolverSoloLosMensajesEntreLosDosUsuariosEnOrden() {
        restTemplate.postForEntity("/api/messages",
                new HttpEntity<>(new SendDirectMessageRequest("usuario-y", "uno"), headersFor("usuario-x")),
                DirectMessageResponse.class);
        restTemplate.postForEntity("/api/messages",
                new HttpEntity<>(new SendDirectMessageRequest("usuario-x", "dos"), headersFor("usuario-y")),
                DirectMessageResponse.class);
        restTemplate.postForEntity("/api/messages",
                new HttpEntity<>(new SendDirectMessageRequest("usuario-z", "con otro"), headersFor("usuario-x")),
                DirectMessageResponse.class);

        ResponseEntity<DirectMessageResponse[]> response = restTemplate.exchange(
                "/api/messages/usuario-y", HttpMethod.GET,
                new HttpEntity<>(headersFor("usuario-x")), DirectMessageResponse[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(List.of(response.getBody())).extracting(DirectMessageResponse::content)
                .containsExactly("uno", "dos");
    }

    @Test
    void endpointProtegidoSinTokenDeberiaDar401() {
        // GET sin body, evita el reset de conexion que puede pasar en POST
        // sin token cuando el filtro corta antes de que el cliente termine
        // de mandar el body (mismo ajuste que se hizo en canvas-service).
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/messages/cualquiera", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void eliminarMensajesDeInvitacionSiendoParticipanteDeberiaDar204YBorrarlos() {
        UUID invitationId = UUID.randomUUID();
        restTemplate.postForEntity("/api/messages/canvas-invitation",
                new HttpEntity<>(new SendCanvasInvitationMessageRequest(
                        "invitado-2", UUID.randomUUID(), "Lienzo", invitationId, "te invito"),
                        headersFor("dueno-2")),
                DirectMessageResponse.class);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/messages/canvas-invitation/" + invitationId, HttpMethod.DELETE,
                new HttpEntity<>(headersFor("invitado-2")), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(directMessageRepository.findByInvitationId(invitationId)).isEmpty();
    }

    @Test
    void eliminarMensajesDeInvitacionSiendoAjenoDeberiaDar403() {
        UUID invitationId = UUID.randomUUID();
        restTemplate.postForEntity("/api/messages/canvas-invitation",
                new HttpEntity<>(new SendCanvasInvitationMessageRequest(
                        "invitado-3", UUID.randomUUID(), "Lienzo", invitationId, "te invito"),
                        headersFor("dueno-3")),
                DirectMessageResponse.class);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/messages/canvas-invitation/" + invitationId, HttpMethod.DELETE,
                new HttpEntity<>(headersFor("intruso")), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(directMessageRepository.findByInvitationId(invitationId)).isNotEmpty();
    }
}
