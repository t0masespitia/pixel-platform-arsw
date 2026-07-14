package edu.eci.arsw.pixelplatform.canvas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "canvas_memberships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"canvasId", "userId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CanvasMembership {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID canvasId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private Instant joinedAt;
}
