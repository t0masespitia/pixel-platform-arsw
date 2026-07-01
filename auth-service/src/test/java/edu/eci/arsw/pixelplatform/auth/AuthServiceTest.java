package edu.eci.arsw.pixelplatform.auth;

import edu.eci.arsw.pixelplatform.auth.dto.AuthResponse;
import edu.eci.arsw.pixelplatform.auth.dto.LoginRequest;
import edu.eci.arsw.pixelplatform.auth.dto.RegisterRequest;
import edu.eci.arsw.pixelplatform.auth.event.DomainEventPublisher;
import edu.eci.arsw.pixelplatform.auth.model.User;
import edu.eci.arsw.pixelplatform.auth.repository.UserRepository;
import edu.eci.arsw.pixelplatform.auth.service.AuthService;
import edu.eci.arsw.pixelplatform.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User savedUser;

    @BeforeEach
    void setUp() {
        savedUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("hashed_password")
                .enabled(true)
                .build();
    }

    @Test
    void registroExitoso_debeRetornarTokenValido() {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "password123");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(savedUser)).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("testuser");
        assertThat(response.email()).isEqualTo("test@example.com");
        verify(domainEventPublisher).publishUserRegistered(1L, "testuser", "test@example.com");
    }

    @Test
    void registroConEmailDuplicado_debeLanzarExcepcion() {
        RegisterRequest request = new RegisterRequest("testuser", "test@example.com", "password123");

        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correo ya esta registrado");

        verify(userRepository, never()).save(any());
    }

    @Test
    void loginExitoso_debeRetornarToken() {
        LoginRequest request = new LoginRequest("test@example.com", "password123");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("password123", "hashed_password")).thenReturn(true);
        when(jwtService.generateToken(savedUser)).thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("testuser");
        verify(domainEventPublisher).publishUserAuthenticated(1L, "testuser");
    }

    @Test
    void loginConPasswordIncorrecto_debeLanzarExcepcion() {
        LoginRequest request = new LoginRequest("test@example.com", "wrongpassword");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(savedUser));
        when(passwordEncoder.matches("wrongpassword", "hashed_password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credenciales invalidas");

        verify(jwtService, never()).generateToken(any());
    }
}
