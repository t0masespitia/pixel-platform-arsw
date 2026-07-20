package edu.eci.arsw.pixelplatform.chat.integration;

import edu.eci.arsw.pixelplatform.chat.model.DirectMessage;
import edu.eci.arsw.pixelplatform.chat.model.DirectMessageType;
import edu.eci.arsw.pixelplatform.chat.repository.DirectMessageRepository;
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
class DirectMessageRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private DirectMessageRepository directMessageRepository;

    private DirectMessage buildMessage(String from, String to, String content, Instant sentAt) {
        return DirectMessage.builder()
                .id(UUID.randomUUID())
                .fromUserId(from)
                .toUserId(to)
                .content(content)
                .sentAt(sentAt)
                .messageType(DirectMessageType.TEXT)
                .build();
    }

    @Test
    void deberiaGuardarYRecuperarMensajePorId() {
        DirectMessage saved = directMessageRepository.save(buildMessage("a", "b", "hola", Instant.now()));

        var found = directMessageRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getContent()).isEqualTo("hola");
    }

    @Test
    void findConversationDeberiaSerBidireccionalYOrdenarPorFechaAscendente() {
        Instant t0 = Instant.now();
        directMessageRepository.save(buildMessage("a", "b", "primero", t0));
        directMessageRepository.save(buildMessage("b", "a", "segundo", t0.plusSeconds(1)));
        directMessageRepository.save(buildMessage("a", "b", "tercero", t0.plusSeconds(2)));
        directMessageRepository.save(buildMessage("a", "c", "con otro usuario", t0.plusSeconds(3)));

        var conversacion = directMessageRepository.findConversation("a", "b");

        assertThat(conversacion).hasSize(3);
        assertThat(conversacion).extracting(DirectMessage::getContent)
                .containsExactly("primero", "segundo", "tercero");
    }

    @Test
    void findByInvitationIdDeberiaRecuperarLosMensajesDeEsaInvitacion() {
        UUID invitationId = UUID.randomUUID();
        DirectMessage invitacion = buildMessage("owner", "invitado", "te invito", Instant.now());
        invitacion.setMessageType(DirectMessageType.CANVAS_INVITATION);
        invitacion.setInvitationId(invitationId);
        directMessageRepository.save(invitacion);
        directMessageRepository.save(buildMessage("owner", "invitado", "mensaje normal", Instant.now()));

        var mensajes = directMessageRepository.findByInvitationId(invitationId);

        assertThat(mensajes).hasSize(1);
        assertThat(mensajes.get(0).getContent()).isEqualTo("te invito");
    }
}
