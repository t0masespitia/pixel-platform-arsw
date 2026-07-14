package edu.eci.arsw.pixelplatform.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageCommand(
        @NotBlank String userId,
        @NotBlank @Size(max = 500) String message
) {
}
