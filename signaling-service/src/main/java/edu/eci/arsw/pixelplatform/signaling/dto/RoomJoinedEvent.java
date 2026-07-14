package edu.eci.arsw.pixelplatform.signaling.dto;

import java.util.List;

public record RoomJoinedEvent(String type, List<String> existingPeers) {
    public RoomJoinedEvent(List<String> existingPeers) {
        this("ROOM_JOINED", existingPeers);
    }
}
