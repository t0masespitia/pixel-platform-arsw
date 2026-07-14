package edu.eci.arsw.pixelplatform.canvas.repository;

import edu.eci.arsw.pixelplatform.canvas.model.Canvas;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CanvasRepository extends JpaRepository<Canvas, UUID> {
    List<Canvas> findByOwnerId(String ownerId);
}
