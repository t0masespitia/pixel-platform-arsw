package edu.eci.arsw.pixelplatform.signaling.registry;

import edu.eci.arsw.pixelplatform.signaling.model.RoomParticipant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SignalingRoomRegistryTest {

    private SignalingRoomRegistry registry;

    private static final UUID CANVAS_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @BeforeEach
    void setUp() {
        registry = new SignalingRoomRegistry();
    }

    @Test
    void primerAddParticipant_retornaSetVacio() {
        Set<String> existing = registry.addParticipant(CANVAS_ID, "user1", "session1");
        assertTrue(existing.isEmpty());
    }

    @Test
    void segundoAddParticipant_retornaSetConSoloPrimero() {
        registry.addParticipant(CANVAS_ID, "user1", "session1");
        Set<String> existing = registry.addParticipant(CANVAS_ID, "user2", "session2");

        assertEquals(1, existing.size());
        assertTrue(existing.contains("user1"));
        assertFalse(existing.contains("user2"));
    }

    @Test
    void getMembersTrasDosAdds_contienAmbos() {
        registry.addParticipant(CANVAS_ID, "user1", "session1");
        registry.addParticipant(CANVAS_ID, "user2", "session2");

        Set<String> members = registry.getMembers(CANVAS_ID);
        assertEquals(2, members.size());
        assertTrue(members.contains("user1"));
        assertTrue(members.contains("user2"));
    }

    @Test
    void removeBySessionId_retornaParticipanteYLoSacaDeMembers() {
        registry.addParticipant(CANVAS_ID, "user1", "session1");

        Optional<RoomParticipant> removed = registry.removeBySessionId("session1");

        assertTrue(removed.isPresent());
        assertEquals("user1", removed.get().userId());
        assertEquals(CANVAS_ID, removed.get().canvasId());
        assertFalse(registry.getMembers(CANVAS_ID).contains("user1"));
    }

    @Test
    void removeBySessionId_sessionDesconocida_retornaEmpty() {
        Optional<RoomParticipant> removed = registry.removeBySessionId("session-inexistente");
        assertTrue(removed.isEmpty());
    }

    @Test
    void removeParticipant_sacaUsuarioDeMembers() {
        registry.addParticipant(CANVAS_ID, "user1", "session1");
        registry.addParticipant(CANVAS_ID, "user2", "session2");

        registry.removeParticipant(CANVAS_ID, "user1");

        Set<String> members = registry.getMembers(CANVAS_ID);
        assertFalse(members.contains("user1"));
        assertTrue(members.contains("user2"));
    }
}
