package edu.eci.arsw.pixelplatform.canvas.integration;

import edu.eci.arsw.pixelplatform.canvas.model.Canvas;
import edu.eci.arsw.pixelplatform.canvas.model.CanvasInvitation;
import edu.eci.arsw.pixelplatform.canvas.model.CanvasMembership;
import edu.eci.arsw.pixelplatform.canvas.model.InvitationStatus;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasInvitationRepository;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasMembershipRepository;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CanvasRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private CanvasRepository canvasRepository;

    @Autowired
    private CanvasMembershipRepository canvasMembershipRepository;

    @Autowired
    private CanvasInvitationRepository canvasInvitationRepository;

    private Canvas buildCanvas(String ownerId) {
        return Canvas.builder()
                .id(UUID.randomUUID())
                .name("Lienzo de " + ownerId)
                .ownerId(ownerId)
                .width(64)
                .height(64)
                .isPrivate(true)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void deberiaGuardarYRecuperarLienzoPorId() {
        Canvas saved = canvasRepository.save(buildCanvas("owner-1"));

        var found = canvasRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getOwnerId()).isEqualTo("owner-1");
    }

    @Test
    void findByOwnerIdDeberiaFiltrarSoloLosLienzosDeEseDueno() {
        canvasRepository.save(buildCanvas("owner-a"));
        canvasRepository.save(buildCanvas("owner-a"));
        canvasRepository.save(buildCanvas("owner-b"));

        var lienzosDeA = canvasRepository.findByOwnerId("owner-a");

        assertThat(lienzosDeA).hasSize(2);
        assertThat(lienzosDeA).allMatch(c -> c.getOwnerId().equals("owner-a"));
    }

    @Test
    void existsByCanvasIdAndUserIdDeberiaDetectarMembresiaExistente() {
        Canvas canvas = canvasRepository.save(buildCanvas("owner-1"));
        canvasMembershipRepository.save(CanvasMembership.builder()
                .id(UUID.randomUUID())
                .canvasId(canvas.getId())
                .userId("miembro-1")
                .joinedAt(Instant.now())
                .build());

        assertThat(canvasMembershipRepository.existsByCanvasIdAndUserId(canvas.getId(), "miembro-1")).isTrue();
        assertThat(canvasMembershipRepository.existsByCanvasIdAndUserId(canvas.getId(), "nadie")).isFalse();
    }

    @Test
    void findByCanvasIdYFindByUserIdDeberianListarMembresiasCorrectamente() {
        Canvas canvasUno = canvasRepository.save(buildCanvas("owner-1"));
        Canvas canvasDos = canvasRepository.save(buildCanvas("owner-2"));
        canvasMembershipRepository.save(CanvasMembership.builder()
                .id(UUID.randomUUID()).canvasId(canvasUno.getId()).userId("compartido")
                .joinedAt(Instant.now()).build());
        canvasMembershipRepository.save(CanvasMembership.builder()
                .id(UUID.randomUUID()).canvasId(canvasDos.getId()).userId("compartido")
                .joinedAt(Instant.now()).build());

        assertThat(canvasMembershipRepository.findByCanvasId(canvasUno.getId())).hasSize(1);
        assertThat(canvasMembershipRepository.findByUserId("compartido")).hasSize(2);
    }

    @Test
    void findByCanvasIdAndUserIdDeberiaRecuperarLaMembresiaExacta() {
        Canvas canvas = canvasRepository.save(buildCanvas("owner-1"));
        canvasMembershipRepository.save(CanvasMembership.builder()
                .id(UUID.randomUUID()).canvasId(canvas.getId()).userId("miembro-x")
                .joinedAt(Instant.now()).build());

        var found = canvasMembershipRepository.findByCanvasIdAndUserId(canvas.getId(), "miembro-x");

        assertThat(found).isPresent();
    }

    @Test
    void findByCodeDeberiaRecuperarLaInvitacionCorrecta() {
        Canvas canvas = canvasRepository.save(buildCanvas("owner-1"));
        canvasInvitationRepository.save(CanvasInvitation.builder()
                .id(UUID.randomUUID())
                .canvasId(canvas.getId())
                .targetUserId("invitado-1")
                .invitedByUserId("owner-1")
                .code("ABC12345")
                .status(InvitationStatus.PENDING)
                .createdAt(Instant.now())
                .build());

        var found = canvasInvitationRepository.findByCode("ABC12345");

        assertThat(found).isPresent();
        assertThat(found.get().getTargetUserId()).isEqualTo("invitado-1");
    }

    @Test
    void findByCanvasIdAndTargetUserIdDeberiaEvitarInvitacionesDuplicadas() {
        Canvas canvas = canvasRepository.save(buildCanvas("owner-1"));
        canvasInvitationRepository.save(CanvasInvitation.builder()
                .id(UUID.randomUUID()).canvasId(canvas.getId()).targetUserId("invitado-2")
                .invitedByUserId("owner-1").code("CODE0001")
                .status(InvitationStatus.PENDING).createdAt(Instant.now()).build());

        var found = canvasInvitationRepository.findByCanvasIdAndTargetUserId(canvas.getId(), "invitado-2");

        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void findByTargetUserIdAndStatusDeberiaFiltrarSoloLasPendientes() {
        Canvas canvasUno = canvasRepository.save(buildCanvas("owner-1"));
        Canvas canvasDos = canvasRepository.save(buildCanvas("owner-2"));
        canvasInvitationRepository.save(CanvasInvitation.builder()
                .id(UUID.randomUUID()).canvasId(canvasUno.getId()).targetUserId("invitado-3")
                .invitedByUserId("owner-1").code("CODE0002")
                .status(InvitationStatus.PENDING).createdAt(Instant.now()).build());
        canvasInvitationRepository.save(CanvasInvitation.builder()
                .id(UUID.randomUUID()).canvasId(canvasDos.getId()).targetUserId("invitado-3")
                .invitedByUserId("owner-2").code("CODE0003")
                .status(InvitationStatus.REJECTED).createdAt(Instant.now())
                .respondedAt(Instant.now()).build());

        var pendientes = canvasInvitationRepository.findByTargetUserIdAndStatus("invitado-3", InvitationStatus.PENDING);

        assertThat(pendientes).hasSize(1);
        assertThat(pendientes.get(0).getCanvasId()).isEqualTo(canvasUno.getId());
    }
}
