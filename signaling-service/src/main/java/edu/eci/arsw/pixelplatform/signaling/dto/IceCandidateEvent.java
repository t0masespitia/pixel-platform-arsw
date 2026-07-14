package edu.eci.arsw.pixelplatform.signaling.dto;

public record IceCandidateEvent(String type, String fromUserId, String candidate) {
    public IceCandidateEvent(String fromUserId, String candidate) {
        this("ICE_CANDIDATE", fromUserId, candidate);
    }
}
