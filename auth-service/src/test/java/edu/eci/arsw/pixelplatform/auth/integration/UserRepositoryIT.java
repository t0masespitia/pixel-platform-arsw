package edu.eci.arsw.pixelplatform.auth.integration;

import edu.eci.arsw.pixelplatform.auth.model.User;
import edu.eci.arsw.pixelplatform.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    private User buildUser(String username, String email, String firstName, String lastName) {
        return User.builder()
                .username(username)
                .email(email)
                .password("hashed-password")
                .firstName(firstName)
                .lastName(lastName)
                .enabled(true)
                .emailVerified(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void deberiaGuardarYRecuperarUsuarioPorEmail() {
        User saved = userRepository.save(buildUser("tomas.espitia", "tomas@test.com", "Tomas", "Espitia"));

        var found = userRepository.findByEmail("tomas@test.com");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getUsername()).isEqualTo("tomas.espitia");
    }

    @Test
    void deberiaDetectarEmailYUsernameExistentes() {
        userRepository.save(buildUser("joel.espitia", "joel@test.com", "Joel", "Espitia"));

        assertThat(userRepository.existsByEmail("joel@test.com")).isTrue();
        assertThat(userRepository.existsByUsername("joel.espitia")).isTrue();
        assertThat(userRepository.existsByEmail("nadie@test.com")).isFalse();
    }

    @Test
    void deberiaExcluirAlUsuarioSolicitanteEnFindByIdNot() {
        User u1 = userRepository.save(buildUser("ana.gomez", "ana@test.com", "Ana", "Gomez"));
        userRepository.save(buildUser("luis.diaz", "luis@test.com", "Luis", "Diaz"));

        var page = userRepository.findByIdNot(u1.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(User::getUsername)
                .doesNotContain("ana.gomez")
                .contains("luis.diaz");
    }

    @Test
    void searchByLetterDeberiaFiltrarPorInicialDeNombre() {
        User excluded = userRepository.save(buildUser("carlos.ruiz", "carlos@test.com", "Carlos", "Ruiz"));
        userRepository.save(buildUser("marta.leon", "marta@test.com", "Marta", "Leon"));
        userRepository.save(buildUser("mario.paez", "mario@test.com", "Mario", "Paez"));

        var page = userRepository.searchByLetter("m", excluded.getId(), PageRequest.of(0, 10));

        assertThat(page.getContent())
                .extracting(User::getFirstName)
                .containsExactlyInAnyOrder("Marta", "Mario");
    }

    @Test
    void searchByQueryDeberiaFiltrarPorNombreApellidoOUsername() {
        User excluded = userRepository.save(buildUser("pedro.vera", "pedro@test.com", "Pedro", "Vera"));
        userRepository.save(buildUser("sofia.rios", "sofia@test.com", "Sofia", "Rios"));

        var porApellido = userRepository.searchByQuery("rios", excluded.getId(), PageRequest.of(0, 10));
        var porUsername = userRepository.searchByQuery("sofia.rios", excluded.getId(), PageRequest.of(0, 10));

        assertThat(porApellido.getContent()).extracting(User::getUsername).containsExactly("sofia.rios");
        assertThat(porUsername.getContent()).extracting(User::getUsername).containsExactly("sofia.rios");
    }
}
