package edu.eci.arsw.pixelplatform.auth.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    private LocalDateTime createdAt;

    @Builder.Default
    private boolean enabled = true;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
