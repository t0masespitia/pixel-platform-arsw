package edu.eci.arsw.pixelplatform.auth.dto;

import jakarta.validation.constraints.*;

public record VerifyEmailRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 6) String code
) {}
