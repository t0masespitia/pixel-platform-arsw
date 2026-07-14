package edu.eci.arsw.pixelplatform.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendDirectMessageRequest(
        @NotBlank String toUserId,
        @NotBlank @Size(max = 1000) String content
) {}
