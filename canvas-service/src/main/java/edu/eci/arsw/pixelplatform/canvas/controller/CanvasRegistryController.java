package edu.eci.arsw.pixelplatform.canvas.controller;

import edu.eci.arsw.pixelplatform.canvas.dto.CanvasResponse;
import edu.eci.arsw.pixelplatform.canvas.dto.CreateCanvasRequest;
import edu.eci.arsw.pixelplatform.canvas.event.DomainEventPublisher;
import edu.eci.arsw.pixelplatform.canvas.model.CanvasConstants;
import edu.eci.arsw.pixelplatform.canvas.service.CanvasService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/canvases")
public class CanvasRegistryController {

    private final CanvasService canvasService;
    private final Counter canvasesCreatedCounter;
    private final DomainEventPublisher domainEventPublisher;

    public CanvasRegistryController(CanvasService canvasService, MeterRegistry meterRegistry,
                                     DomainEventPublisher domainEventPublisher) {
        this.canvasService = canvasService;
        this.canvasesCreatedCounter = Counter.builder("pixelplatform.canvases.new")
                .description("Total de lienzos creados")
                .register(meterRegistry);
        this.domainEventPublisher = domainEventPublisher;
    }

    @PostMapping
    public ResponseEntity<?> createCanvas(@Valid @RequestBody CreateCanvasRequest request,
                                          HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        if (!verifiedUserId.equals(request.ownerId())) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "El usuario del token no coincide con el userId de la peticion"));
        }
        CanvasResponse created = canvasService.createCanvas(request);
        canvasesCreatedCounter.increment();
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping("/general")
    public ResponseEntity<?> getGeneralCanvas() {
        try {
            return ResponseEntity.ok(canvasService.getCanvas(CanvasConstants.GENERAL_CANVAS_ID));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getCanvas(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(canvasService.getCanvas(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping
    public ResponseEntity<?> getCanvasesByOwner(@RequestParam String ownerId,
                                                 HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        if (!verifiedUserId.equals(ownerId)) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "El usuario del token no coincide con el userId de la peticion"));
        }
        return ResponseEntity.ok(canvasService.getCanvasesByOwner(ownerId));
    }

    @GetMapping("/shared")
    public ResponseEntity<?> getSharedCanvases(@RequestParam String userId,
                                                HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        if (!verifiedUserId.equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "El usuario del token no coincide con el userId de la peticion"));
        }
        return ResponseEntity.ok(canvasService.getSharedCanvases(userId));
    }

    @GetMapping("/{id}/access")
    public ResponseEntity<?> checkAccess(@PathVariable UUID id, @RequestParam String userId,
                                         HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        if (!verifiedUserId.equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "El usuario del token no coincide con el userId de la peticion"));
        }
        try {
            boolean allowed = canvasService.hasAccess(id, userId);
            return ResponseEntity.ok(Map.of("allowed", allowed));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<?> getHistory(@PathVariable UUID id,
                                         @RequestParam String userId,
                                         @RequestParam(defaultValue = "500") int limit,
                                         HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        if (!verifiedUserId.equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "El usuario del token no coincide con el userId de la peticion"));
        }
        try {
            if (!canvasService.hasAccess(id, userId)) {
                return ResponseEntity.status(403).body(Map.of("error",
                        "El usuario no tiene acceso a este lienzo"));
            }
            List<Map<String, String>> history = domainEventPublisher.getHistory(id, limit);
            return ResponseEntity.ok(Map.of("canvasId", id, "events", history));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteCanvas(@PathVariable UUID id, HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        try {
            CanvasResponse canvas = canvasService.getCanvas(id);
            if (canvas.ownerId() == null || !verifiedUserId.equals(canvas.ownerId())) {
                return ResponseEntity.status(403).body(Map.of("error",
                        "El usuario del token no coincide con el userId de la peticion"));
            }
            canvasService.deleteCanvas(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
