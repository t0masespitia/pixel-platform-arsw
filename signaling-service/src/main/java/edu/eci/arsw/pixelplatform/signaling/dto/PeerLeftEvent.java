package edu.eci.arsw.pixelplatform.signaling.dto;

public record PeerLeftEvent(String type, String userId) {
    public PeerLeftEvent(String userId) {
        this("PEER_LEFT", userId);
    }
}
