package edu.eci.arsw.pixelplatform.canvas.repository;

import edu.eci.arsw.pixelplatform.canvas.model.CanvasInvitation;
import edu.eci.arsw.pixelplatform.canvas.model.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CanvasInvitationRepository extends JpaRepository<CanvasInvitation, UUID> {
    Optional<CanvasInvitation> findByCode(String code);
    Optional<CanvasInvitation> findByCanvasIdAndTargetUserId(UUID canvasId, String targetUserId);
    List<CanvasInvitation> findByCanvasId(UUID canvasId);
    List<CanvasInvitation> findByTargetUserIdAndStatus(String targetUserId, InvitationStatus status);
}
