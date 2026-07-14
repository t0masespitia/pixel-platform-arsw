package edu.eci.arsw.pixelplatform.auth.controller;

import edu.eci.arsw.pixelplatform.auth.dto.AuthResponse;
import edu.eci.arsw.pixelplatform.auth.dto.LoginRequest;
import edu.eci.arsw.pixelplatform.auth.dto.RegisterRequest;
import edu.eci.arsw.pixelplatform.auth.dto.RegisterResponse;
import edu.eci.arsw.pixelplatform.auth.dto.ResendCodeRequest;
import edu.eci.arsw.pixelplatform.auth.dto.SendInvitationEmailRequest;
import edu.eci.arsw.pixelplatform.auth.dto.UserLookupResponse;
import edu.eci.arsw.pixelplatform.auth.dto.VerifyEmailRequest;
import org.springframework.web.bind.annotation.RequestParam;
import edu.eci.arsw.pixelplatform.auth.exception.EmailNotVerifiedException;
import edu.eci.arsw.pixelplatform.auth.service.AuthService;
import edu.eci.arsw.pixelplatform.auth.service.JwtService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final Counter usersRegisteredCounter;

    public AuthController(AuthService authService, JwtService jwtService, MeterRegistry meterRegistry) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.usersRegisteredCounter = Counter.builder("pixelplatform.users.registered")
                .description("Total de usuarios registrados")
                .register(meterRegistry);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            RegisterResponse response = authService.register(request);
            usersRegisteredCounter.increment();
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (EmailNotVerifiedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", e.getMessage(), "emailVerified", false));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        try {
            return ResponseEntity.ok(authService.verifyEmail(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/resend-code")
    public ResponseEntity<?> resendCode(@Valid @RequestBody ResendCodeRequest request) {
        try {
            return ResponseEntity.ok(authService.resendCode(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/users/lookup")
    public ResponseEntity<?> lookupByEmail(@RequestParam String email) {
        try {
            return ResponseEntity.ok(authService.lookupByEmail(email));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ") ||
                !jwtService.isTokenValid(authHeader.substring(7))) {
            return ResponseEntity.status(401).body(Map.of("error", "Token invalido o expirado"));
        }
        return ResponseEntity.ok(authService.getAllUsers());
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getUserById(@PathVariable String userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ") ||
                !jwtService.isTokenValid(authHeader.substring(7))) {
            return ResponseEntity.status(401).body(Map.of("error", "Token invalido o expirado"));
        }
        try {
            return ResponseEntity.ok(authService.getUserById(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/notifications/send-invitation-email")
    public ResponseEntity<?> sendInvitationEmail(@Valid @RequestBody SendInvitationEmailRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ") ||
                !jwtService.isTokenValid(authHeader.substring(7))) {
            return ResponseEntity.status(401).body(Map.of("error", "Token invalido o expirado"));
        }
        try {
            authService.sendInvitationNotificationEmail(request);
            return ResponseEntity.ok(Map.of("message", "Correo de invitacion enviado"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/users/directory")
    public ResponseEntity<?> getDirectory(
            @RequestParam(required = false) String letter,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam String requesterId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ") ||
                !jwtService.isTokenValid(authHeader.substring(7))) {
            return ResponseEntity.status(401).body(Map.of("error", "Token invalido o expirado"));
        }
        return ResponseEntity.ok(authService.searchDirectory(requesterId, letter, query, page, size));
    }
}
