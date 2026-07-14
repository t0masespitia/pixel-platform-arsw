package edu.eci.arsw.pixelplatform.signaling.dto;

public record PeerJoinedEvent(String type, String userId) {
    public PeerJoinedEvent(String userId) {
        this("PEER_JOINED", userId);
    }
}
