package edu.eci.arsw.pixelplatform.auth.service;

import edu.eci.arsw.pixelplatform.auth.dto.AuthResponse;
import edu.eci.arsw.pixelplatform.auth.dto.LoginRequest;
import edu.eci.arsw.pixelplatform.auth.dto.RegisterRequest;
import edu.eci.arsw.pixelplatform.auth.event.DomainEventPublisher;
import edu.eci.arsw.pixelplatform.auth.model.User;
import edu.eci.arsw.pixelplatform.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final DomainEventPublisher domainEventPublisher;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository,
                       JwtService jwtService,
                       DomainEventPublisher domainEventPublisher,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.domainEventPublisher = domainEventPublisher;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("El correo ya esta registrado");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("El nombre de usuario ya esta en uso");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .build();

        user = userRepository.save(user);

        domainEventPublisher.publishUserRegistered(user.getId(), user.getUsername(), user.getEmail());

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getUsername(), user.getEmail(), expirationMs);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Credenciales invalidas"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("Credenciales invalidas");
        }

        domainEventPublisher.publishUserAuthenticated(user.getId(), user.getUsername());

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getUsername(), user.getEmail(), expirationMs);
    }
}
