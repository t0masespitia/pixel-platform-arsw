package edu.eci.arsw.pixelplatform.canvas.service;

import edu.eci.arsw.pixelplatform.canvas.dto.CanvasResponse;
import edu.eci.arsw.pixelplatform.canvas.dto.CreateCanvasRequest;
import edu.eci.arsw.pixelplatform.canvas.model.Canvas;
import edu.eci.arsw.pixelplatform.canvas.model.CanvasConstants;
import edu.eci.arsw.pixelplatform.canvas.model.CanvasMembership;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasInvitationRepository;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasMembershipRepository;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CanvasService {

    private final CanvasRepository canvasRepository;
    private final CanvasMembershipRepository canvasMembershipRepository;
    private final CanvasInvitationRepository canvasInvitationRepository;
    private final CanvasStateService canvasStateService;

    public CanvasService(CanvasRepository canvasRepository,
                         CanvasMembershipRepository canvasMembershipRepository,
                         CanvasInvitationRepository canvasInvitationRepository,
                         CanvasStateService canvasStateService) {
        this.canvasRepository = canvasRepository;
        this.canvasMembershipRepository = canvasMembershipRepository;
        this.canvasInvitationRepository = canvasInvitationRepository;
        this.canvasStateService = canvasStateService;
    }

    public CanvasResponse createCanvas(CreateCanvasRequest request) {
        Canvas canvas = new Canvas();
        canvas.setId(UUID.randomUUID());
        canvas.setName(request.name());
        canvas.setOwnerId(request.ownerId());
        canvas.setWidth(request.width());
        canvas.setHeight(request.height());
        canvas.setPrivate(true);
        canvas.setCreatedAt(Instant.now());
        Canvas saved = canvasRepository.save(canvas);

        if (saved.getOwnerId() != null) {
            CanvasMembership membership = CanvasMembership.builder()
                    .id(UUID.randomUUID())
                    .canvasId(saved.getId())
                    .userId(saved.getOwnerId())
                    .joinedAt(Instant.now())
                    .build();
            canvasMembershipRepository.save(membership);
        }

        return toResponse(saved);
    }

    public CanvasResponse getCanvas(UUID id) {
        return canvasRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Lienzo no encontrado"));
    }

    public List<CanvasResponse> getCanvasesByOwner(String ownerId) {
        return canvasRepository.findByOwnerId(ownerId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<CanvasResponse> getSharedCanvases(String userId) {
        return canvasMembershipRepository.findByUserId(userId).stream()
                .map(m -> canvasRepository.findById(m.getCanvasId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(c -> !userId.equals(c.getOwnerId()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public boolean hasAccess(UUID canvasId, String userId) {
        Canvas canvas = canvasRepository.findById(canvasId)
                .orElseThrow(() -> new IllegalArgumentException("Lienzo no encontrado"));
        if (!canvas.isPrivate()) {
            return true;
        }
        return canvasMembershipRepository.existsByCanvasIdAndUserId(canvasId, userId);
    }

    @Transactional
    public void deleteCanvas(UUID id) {
        if (id.equals(CanvasConstants.GENERAL_CANVAS_ID)) {
            throw new IllegalArgumentException("No se puede eliminar el lienzo general");
        }
        Canvas canvas = canvasRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lienzo no encontrado"));
        if (canvas.isDefaultTemplate()) {
            throw new IllegalArgumentException("No se puede eliminar un lienzo predeterminado");
        }
        canvasMembershipRepository.deleteAll(canvasMembershipRepository.findByCanvasId(id));
        canvasInvitationRepository.deleteAll(canvasInvitationRepository.findByCanvasId(id));
        canvasRepository.deleteById(id);
        canvasStateService.evictFromCache(id);
    }

    private CanvasResponse toResponse(Canvas canvas) {
        return new CanvasResponse(
                canvas.getId(),
                canvas.getName(),
                canvas.getOwnerId(),
                canvas.getWidth(),
                canvas.getHeight(),
                canvas.isPrivate(),
                canvas.isDefaultTemplate(),
                canvas.getCreatedAt()
        );
    }
}
