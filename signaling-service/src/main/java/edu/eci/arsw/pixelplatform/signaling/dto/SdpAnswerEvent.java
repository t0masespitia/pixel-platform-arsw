package edu.eci.arsw.pixelplatform.signaling.dto;

public record SdpAnswerEvent(String type, String fromUserId, String sdp) {
    public SdpAnswerEvent(String fromUserId, String sdp) {
        this("SDP_ANSWER", fromUserId, sdp);
    }
}
