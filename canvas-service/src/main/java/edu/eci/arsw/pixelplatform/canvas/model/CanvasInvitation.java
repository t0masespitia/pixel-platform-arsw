package edu.eci.arsw.pixelplatform.canvas.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "canvas_invitations",
        uniqueConstraints = @UniqueConstraint(columnNames = {"canvasId", "targetUserId"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CanvasInvitation {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID canvasId;

    @Column(nullable = false)
    private String targetUserId;

    @Column(nullable = false)
    private String invitedByUserId;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant respondedAt;
}
