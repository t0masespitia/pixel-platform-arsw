package edu.eci.arsw.pixelplatform.signaling.dto;

public record SdpOfferEvent(String type, String fromUserId, String sdp) {
    public SdpOfferEvent(String fromUserId, String sdp) {
        this("SDP_OFFER", fromUserId, sdp);
    }
}
