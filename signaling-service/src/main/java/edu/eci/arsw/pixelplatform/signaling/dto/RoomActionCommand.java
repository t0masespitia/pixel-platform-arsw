package edu.eci.arsw.pixelplatform.signaling.dto;

import jakarta.validation.constraints.NotBlank;

public record RoomActionCommand(@NotBlank String userId) {
}
