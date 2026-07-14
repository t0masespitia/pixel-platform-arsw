package edu.eci.arsw.pixelplatform.signaling.registry;

import edu.eci.arsw.pixelplatform.signaling.model.RoomParticipant;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SignalingRoomRegistry {

    private final Map<UUID, Set<String>> roomMembers = new ConcurrentHashMap<>();
    private final Map<String, RoomParticipant> sessionParticipants = new ConcurrentHashMap<>();

    public synchronized Set<String> addParticipant(UUID canvasId, String userId, String sessionId) {
        Set<String> existing = roomMembers.computeIfAbsent(canvasId, k -> ConcurrentHashMap.newKeySet());
        Set<String> snapshot = new HashSet<>(existing);
        existing.add(userId);
        sessionParticipants.put(sessionId, new RoomParticipant(canvasId, userId));
        return snapshot;
    }

    public Optional<RoomParticipant> removeBySessionId(String sessionId) {
        RoomParticipant participant = sessionParticipants.remove(sessionId);
        if (participant == null) {
            return Optional.empty();
        }
        Set<String> members = roomMembers.get(participant.canvasId());
        if (members != null) {
            members.remove(participant.userId());
            if (members.isEmpty()) {
                roomMembers.remove(participant.canvasId());
            }
        }
        return Optional.of(participant);
    }

    public void removeParticipant(UUID canvasId, String userId) {
        Set<String> members = roomMembers.get(canvasId);
        if (members != null) {
            members.remove(userId);
            if (members.isEmpty()) {
                roomMembers.remove(canvasId);
            }
        }
    }

    public Set<String> getMembers(UUID canvasId) {
        Set<String> members = roomMembers.get(canvasId);
        if (members == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(members);
    }
}
