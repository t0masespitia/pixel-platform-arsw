package edu.eci.arsw.pixelplatform.auth;

import edu.eci.arsw.pixelplatform.auth.dto.AuthResponse;
import edu.eci.arsw.pixelplatform.auth.dto.LoginRequest;
import edu.eci.arsw.pixelplatform.auth.dto.RegisterRequest;
import edu.eci.arsw.pixelplatform.auth.dto.RegisterResponse;
import edu.eci.arsw.pixelplatform.auth.event.DomainEventPublisher;
import edu.eci.arsw.pixelplatform.auth.exception.EmailNotVerifiedException;
import edu.eci.arsw.pixelplatform.auth.model.User;
import edu.eci.arsw.pixelplatform.auth.repository.UserRepository;
import edu.eci.arsw.pixelplatform.auth.service.AuthService;
import edu.eci.arsw.pixelplatform.auth.service.EmailService;
import edu.eci.arsw.pixelplatform.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    private User savedUser;

    @BeforeEach
    void setUp() {
        savedUser = User.builder()
                .id(1L)
                .firstName("Test")
                .lastName("User")
                .username("test.user")
                .email("test@example.com")
                .password("hashed_password")
                .enabled(true)
                .emailVerified(true)
                .build();
    }

    @Test
    void registroExitoso_debeCrearUsuarioYEnviarCodigoSinGenerarToken() {
        RegisterRequest request = new RegisterRequest(
                "Test",
                "User",
                "test@example.com",
                "Password123"
        );

        when(userRepository.existsByEmail("test@example.com"))
                .thenReturn(false);
        when(userRepository.existsByUsername("test.user"))
                .thenReturn(false);
        when(passwordEncoder.encode("Password123"))
                .thenReturn("hashed_password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(1L);
                    return user;
                });

        RegisterResponse response = authService.register(request);

        assertThat(response.email())
                .isEqualTo("test@example.com");

        assertThat(response.message())
                .contains("Registro exitoso");

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User createdUser = userCaptor.getValue();

        assertThat(createdUser.getId())
                .isEqualTo(1L);

        assertThat(createdUser.getFirstName())
                .isEqualTo("Test");

        assertThat(createdUser.getLastName())
                .isEqualTo("User");

        assertThat(createdUser.getUsername())
                .isEqualTo("test.user");

        assertThat(createdUser.getEmail())
                .isEqualTo("test@example.com");

        assertThat(createdUser.getPassword())
                .isEqualTo("hashed_password");

        assertThat(createdUser.isEmailVerified())
                .isFalse();

        assertThat(createdUser.getVerificationCode())
                .matches("\\d{6}");

        assertThat(createdUser.getVerificationCodeExpiresAt())
                .isNotNull()
                .isAfter(LocalDateTime.now());

        ArgumentCaptor<String> codeCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(emailService).sendVerificationCode(
                eq("test@example.com"),
                codeCaptor.capture()
        );

        assertThat(codeCaptor.getValue())
                .isEqualTo(createdUser.getVerificationCode());

        verify(domainEventPublisher).publishUserRegistered(
                1L,
                "test.user",
                "test@example.com"
        );

        verify(jwtService, never())
                .generateToken(any(User.class));
    }

    @Test
    void registroConEmailDuplicado_debeLanzarExcepcion() {
        RegisterRequest request = new RegisterRequest(
                "Test",
                "User",
                "test@example.com",
                "Password123"
        );

        when(userRepository.existsByEmail("test@example.com"))
                .thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("correo ya esta registrado");

        verify(userRepository, never())
                .save(any(User.class));

        verify(emailService, never())
                .sendVerificationCode(any(), any());

        verify(domainEventPublisher, never())
                .publishUserRegistered(any(), any(), any());

        verify(jwtService, never())
                .generateToken(any(User.class));
    }

    @Test
    void loginExitoso_debeRetornarToken() {
        LoginRequest request = new LoginRequest(
                "test@example.com",
                "Password123"
        );

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(savedUser));

        when(passwordEncoder.matches(
                "Password123",
                "hashed_password"
        )).thenReturn(true);

        when(jwtService.generateToken(savedUser))
                .thenReturn("jwt-token");

        AuthResponse response = authService.login(request);

        assertThat(response.token())
                .isEqualTo("jwt-token");

        assertThat(response.username())
                .isEqualTo("test.user");

        assertThat(response.email())
                .isEqualTo("test@example.com");

        verify(domainEventPublisher)
                .publishUserAuthenticated(1L, "test.user");

        verify(jwtService)
                .generateToken(savedUser);
    }

    @Test
    void loginConPasswordIncorrecto_debeLanzarExcepcion() {
        LoginRequest request = new LoginRequest(
                "test@example.com",
                "WrongPassword123"
        );

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(savedUser));

        when(passwordEncoder.matches(
                "WrongPassword123",
                "hashed_password"
        )).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credenciales invalidas");

        verify(jwtService, never())
                .generateToken(any(User.class));

        verify(domainEventPublisher, never())
                .publishUserAuthenticated(any(), any());
    }

    @Test
    void loginConEmailNoVerificado_debeLanzarExcepcion() {
        savedUser.setEmailVerified(false);

        LoginRequest request = new LoginRequest(
                "test@example.com",
                "Password123"
        );

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(savedUser));

        when(passwordEncoder.matches(
                "Password123",
                "hashed_password"
        )).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(EmailNotVerifiedException.class)
                .hasMessage(
                        "Debes verificar tu correo antes de iniciar sesion"
                );

        verify(jwtService, never())
                .generateToken(any(User.class));

        verify(domainEventPublisher, never())
                .publishUserAuthenticated(any(), any());
    }
}
