package edu.eci.arsw.pixelplatform.auth.service;

import edu.eci.arsw.pixelplatform.auth.dto.AuthResponse;
import edu.eci.arsw.pixelplatform.auth.dto.DirectoryPageResponse;
import edu.eci.arsw.pixelplatform.auth.dto.LoginRequest;
import edu.eci.arsw.pixelplatform.auth.dto.RegisterRequest;
import edu.eci.arsw.pixelplatform.auth.dto.RegisterResponse;
import edu.eci.arsw.pixelplatform.auth.dto.ResendCodeRequest;
import edu.eci.arsw.pixelplatform.auth.dto.SendInvitationEmailRequest;
import edu.eci.arsw.pixelplatform.auth.dto.UserDirectoryEntry;
import edu.eci.arsw.pixelplatform.auth.dto.UserLookupResponse;
import edu.eci.arsw.pixelplatform.auth.dto.UserProfileResponse;
import edu.eci.arsw.pixelplatform.auth.dto.UserSummaryResponse;
import edu.eci.arsw.pixelplatform.auth.dto.VerifyEmailRequest;
import edu.eci.arsw.pixelplatform.auth.client.CanvasServiceClient;
import edu.eci.arsw.pixelplatform.auth.event.DomainEventPublisher;
import edu.eci.arsw.pixelplatform.auth.exception.EmailNotVerifiedException;
import edu.eci.arsw.pixelplatform.auth.model.User;
import edu.eci.arsw.pixelplatform.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final DomainEventPublisher domainEventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final CanvasServiceClient canvasServiceClient;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       JwtService jwtService,
                       DomainEventPublisher domainEventPublisher,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService,
                       CanvasServiceClient canvasServiceClient) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.domainEventPublisher = domainEventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.canvasServiceClient = canvasServiceClient;
    }

    public RegisterResponse register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new IllegalArgumentException("Las contrasenas no coinciden");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("El correo ya esta registrado");
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        String nickname = (request.nickname() != null && !request.nickname().isBlank())
                ? request.nickname().trim()
                : null;

        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .username(generateUniqueUsername(request.firstName(), request.lastName()))
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(nickname)
                .verificationCode(code)
                .verificationCodeExpiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        user = userRepository.save(user);

        domainEventPublisher.publishUserRegistered(user.getId(), user.getUsername(), user.getEmail());

        emailService.sendVerificationCode(user.getEmail(), code);

        return new RegisterResponse(
                "Registro exitoso. Revisa tu correo para confirmar tu cuenta.",
                user.getEmail()
        );
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Credenciales invalidas"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("Credenciales invalidas");
        }

        if (!user.isEmailVerified()) {
            throw new EmailNotVerifiedException("Debes verificar tu correo antes de iniciar sesion");
        }

        domainEventPublisher.publishUserAuthenticated(user.getId(), user.getUsername());

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getUsername(), user.getEmail(), expirationMs);
    }

    public AuthResponse verifyEmail(VerifyEmailRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("El correo ya fue verificado");
        }

        if (user.getVerificationCode() == null || !user.getVerificationCode().equals(request.code())) {
            throw new IllegalArgumentException("Codigo invalido");
        }

        if (LocalDateTime.now().isAfter(user.getVerificationCodeExpiresAt())) {
            throw new IllegalArgumentException("El codigo ha expirado, solicita uno nuevo");
        }

        user.setEmailVerified(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiresAt(null);
        userRepository.save(user);

        String token = jwtService.generateToken(user);

        try {
            canvasServiceClient.createDefaultCanvases(String.valueOf(user.getId()), "Bearer " + token);
        } catch (Exception e) {
            log.warn("No se pudieron crear los lienzos predeterminados para el usuario {}: {}",
                    user.getId(), e.getMessage());
        }

        return new AuthResponse(token, user.getUsername(), user.getEmail(), expirationMs);
    }

    public Map<String, String> resendCode(ResendCodeRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (user.isEmailVerified()) {
            throw new IllegalArgumentException("El correo ya fue verificado");
        }

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        user.setVerificationCode(code);
        user.setVerificationCodeExpiresAt(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        emailService.sendVerificationCode(user.getEmail(), code);

        return Map.of("message", "Se reenvio el codigo de verificacion");
    }

    private String generateUniqueUsername(String firstName, String lastName) {
        String base = normalizeForUsername(firstName) + "." + normalizeForUsername(lastName);
        String candidate = base;
        int suffix = 2;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + suffix;
            suffix++;
        }
        return candidate;
    }

    private String normalizeForUsername(String input) {
        String trimmed = input == null ? "" : input.trim().toLowerCase();
        String normalized = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("[^a-z0-9]", "");
    }

    public List<UserSummaryResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(u -> new UserSummaryResponse(String.valueOf(u.getId()), u.getUsername(),
                        u.getFirstName(), u.getLastName(), u.getAvatarUrl(), u.getNickname()))
                .toList();
    }

    public UserLookupResponse lookupByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        return new UserLookupResponse(String.valueOf(user.getId()), user.getUsername(), user.getEmail());
    }

    public UserProfileResponse getUserById(String userId) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        return new UserProfileResponse(String.valueOf(user.getId()), user.getUsername(),
                user.getFirstName(), user.getLastName(), user.getAvatarUrl(), user.getNickname());
    }

    public void sendInvitationNotificationEmail(SendInvitationEmailRequest request) {
        User target = userRepository.findById(Long.parseLong(request.targetUserId()))
                .orElseThrow(() -> new IllegalArgumentException("Usuario destino no encontrado"));
        emailService.sendInvitationEmail(target.getEmail(), request.inviterName(),
                request.canvasName(), request.code(), request.joinLink());
    }

    public DirectoryPageResponse searchDirectory(String requesterId, String letter,
            String query, int page, int size) {
        Long excludedId = Long.parseLong(requesterId);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by("firstName").ascending().and(Sort.by("lastName").ascending()));
        Page<User> result;
        if (query != null && !query.isBlank()) {
            result = userRepository.searchByQuery(query.trim(), excludedId, pageable);
        } else if (letter != null && !letter.isBlank()) {
            result = userRepository.searchByLetter(letter.trim(), excludedId, pageable);
        } else {
            result = userRepository.findByIdNot(excludedId, pageable);
        }
        List<UserDirectoryEntry> entries = result.getContent().stream()
                .map(u -> new UserDirectoryEntry(String.valueOf(u.getId()), u.getUsername(),
                        u.getFirstName(), u.getLastName(), u.getAvatarUrl(), u.getNickname()))
                .toList();
        return new DirectoryPageResponse(entries, page, size,
                result.getTotalElements(), result.getTotalPages());
    }
}
