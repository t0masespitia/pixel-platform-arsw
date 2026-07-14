package edu.eci.arsw.pixelplatform.auth.controller;

import edu.eci.arsw.pixelplatform.auth.service.AvatarService;
import edu.eci.arsw.pixelplatform.auth.service.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/me/avatar")
public class AvatarController {

    private final AvatarService avatarService;
    private final JwtService jwtService;

    public AvatarController(AvatarService avatarService, JwtService jwtService) {
        this.avatarService = avatarService;
        this.jwtService = jwtService;
    }

    @PostMapping
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file,
            @RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ") ||
                !jwtService.isTokenValid(authHeader.substring(7))) {
            return ResponseEntity.status(401).body(Map.of("error", "Token invalido o expirado"));
        }
        String userId = jwtService.extractUserId(authHeader.substring(7));
        try {
            String avatarUrl = avatarService.uploadAvatar(userId, file);
            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al guardar la imagen"));
        }
    }

    @DeleteMapping
    public ResponseEntity<?> deleteAvatar(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ") ||
                !jwtService.isTokenValid(authHeader.substring(7))) {
            return ResponseEntity.status(401).body(Map.of("error", "Token invalido o expirado"));
        }
        String userId = jwtService.extractUserId(authHeader.substring(7));
        try {
            avatarService.deleteAvatar(userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Error al eliminar la imagen"));
        }
    }
}
