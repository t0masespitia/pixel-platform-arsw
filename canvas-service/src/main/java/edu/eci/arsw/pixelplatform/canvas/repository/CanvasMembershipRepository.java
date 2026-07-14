package edu.eci.arsw.pixelplatform.canvas.repository;

import edu.eci.arsw.pixelplatform.canvas.model.CanvasMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CanvasMembershipRepository extends JpaRepository<CanvasMembership, UUID> {
    boolean existsByCanvasIdAndUserId(UUID canvasId, String userId);
    List<CanvasMembership> findByCanvasId(UUID canvasId);
    Optional<CanvasMembership> findByCanvasIdAndUserId(UUID canvasId, String userId);
    List<CanvasMembership> findByUserId(String userId);
}
