package edu.eci.arsw.pixelplatform.canvas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "canvases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Canvas {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String ownerId;

    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    @Column(nullable = false)
    private boolean isPrivate;

    @Column(nullable = false)
    private Instant createdAt;
}
