package edu.eci.arsw.pixelplatform.canvas.controller;

import edu.eci.arsw.pixelplatform.canvas.dto.PixelDTO;
import edu.eci.arsw.pixelplatform.canvas.service.CanvasStateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/canvas")
public class CanvasController {

    private final CanvasStateService canvasStateService;

    public CanvasController(CanvasStateService canvasStateService) {
        this.canvasStateService = canvasStateService;
    }

    @GetMapping("/state")
    public ResponseEntity<?> getCanvasState() {
        return ResponseEntity.ok(canvasStateService.getCanvasState());
    }

    @PostMapping("/pixel")
    public ResponseEntity<?> paintPixel(@Valid @RequestBody PixelDTO pixel) {
        try {
            canvasStateService.paintPixel(pixel);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
