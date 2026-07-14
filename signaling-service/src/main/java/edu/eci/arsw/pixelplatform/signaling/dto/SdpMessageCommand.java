package edu.eci.arsw.pixelplatform.signaling.dto;

import jakarta.validation.constraints.NotBlank;

public record SdpMessageCommand(
        @NotBlank String fromUserId,
        @NotBlank String toUserId,
        @NotBlank String sdp
) {
}
