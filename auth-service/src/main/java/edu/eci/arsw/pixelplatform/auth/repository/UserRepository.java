package edu.eci.arsw.pixelplatform.auth.repository;

import edu.eci.arsw.pixelplatform.auth.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    Page<User> findByIdNot(Long excludedId, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.id <> :excludedId AND " +
           "LOWER(u.firstName) LIKE LOWER(CONCAT(:letter, '%'))")
    Page<User> searchByLetter(@Param("letter") String letter,
                               @Param("excludedId") Long excludedId,
                               Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.id <> :excludedId AND (" +
           "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<User> searchByQuery(@Param("q") String query,
                              @Param("excludedId") Long excludedId,
                              Pageable pageable);
}
