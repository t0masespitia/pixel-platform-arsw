package edu.eci.arsw.pixelplatform.canvas.controller;

import edu.eci.arsw.pixelplatform.canvas.dto.BulkPixelUpdateRequest;
import edu.eci.arsw.pixelplatform.canvas.dto.CanvasStateDTO;
import edu.eci.arsw.pixelplatform.canvas.dto.PixelDTO;
import edu.eci.arsw.pixelplatform.canvas.service.CanvasStateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/canvas")
public class CanvasController {

    private final CanvasStateService canvasStateService;
    private final SimpMessagingTemplate messagingTemplate;

    public CanvasController(CanvasStateService canvasStateService,
                            SimpMessagingTemplate messagingTemplate) {
        this.canvasStateService = canvasStateService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/{canvasId}/state")
    public ResponseEntity<?> getCanvasState(@PathVariable UUID canvasId) {
        try {
            return ResponseEntity.ok(canvasStateService.getCanvasState(canvasId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{canvasId}/pixel")
    public ResponseEntity<?> paintPixel(@PathVariable UUID canvasId,
                                         @Valid @RequestBody PixelDTO pixel) {
        try {
            canvasStateService.paintPixel(canvasId, pixel);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{canvasId}/pixels/bulk")
    public ResponseEntity<?> bulkSetPixels(@PathVariable UUID canvasId,
                                            @Valid @RequestBody BulkPixelUpdateRequest request,
                                            HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        if (!verifiedUserId.equals(request.requesterId())) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "El usuario del token no coincide con el userId de la peticion"));
        }
        try {
            CanvasStateDTO updated = canvasStateService.bulkSetPixels(canvasId,
                    request.requesterId(), request.pixels());
            messagingTemplate.convertAndSend("/topic/canvas/" + canvasId + "/bulk-update", updated);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
