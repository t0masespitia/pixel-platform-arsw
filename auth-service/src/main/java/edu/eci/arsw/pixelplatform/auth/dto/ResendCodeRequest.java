package edu.eci.arsw.pixelplatform.auth.dto;

import jakarta.validation.constraints.*;

public record ResendCodeRequest(@NotBlank @Email String email) {}
