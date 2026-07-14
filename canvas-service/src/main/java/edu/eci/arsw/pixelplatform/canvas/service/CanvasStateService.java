package edu.eci.arsw.pixelplatform.canvas.service;

import edu.eci.arsw.pixelplatform.canvas.dto.CanvasStateDTO;
import edu.eci.arsw.pixelplatform.canvas.dto.PixelDTO;
import edu.eci.arsw.pixelplatform.canvas.dto.PixelPaintCommand;
import edu.eci.arsw.pixelplatform.canvas.event.DomainEventPublisher;
import edu.eci.arsw.pixelplatform.canvas.exception.CooldownActiveException;
import edu.eci.arsw.pixelplatform.canvas.model.Canvas;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasMembershipRepository;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class CanvasStateService {

    private static final String PIXELS_PREFIX = "canvas:pixels:";
    private static final String COOLDOWN_PREFIX = "canvas:cooldown:";

    private final RedisTemplate<String, String> redisTemplate;
    private final HashOperations<String, String, String> hashOps;
    private final ValueOperations<String, String> valueOps;
    private final CanvasRepository canvasRepository;
    private final CanvasMembershipRepository canvasMembershipRepository;
    private final DomainEventPublisher domainEventPublisher;

    @Value("${canvas.cooldown-millis}")
    private long cooldownMillis;

    public CanvasStateService(RedisTemplate<String, String> redisTemplate,
                               CanvasRepository canvasRepository,
                               CanvasMembershipRepository canvasMembershipRepository,
                               DomainEventPublisher domainEventPublisher) {
        this.redisTemplate = redisTemplate;
        this.hashOps = redisTemplate.opsForHash();
        this.valueOps = redisTemplate.opsForValue();
        this.canvasRepository = canvasRepository;
        this.canvasMembershipRepository = canvasMembershipRepository;
        this.domainEventPublisher = domainEventPublisher;
    }

    public CanvasStateDTO getCanvasState(UUID canvasId) {
        canvasRepository.findById(canvasId)
                .orElseThrow(() -> new IllegalArgumentException("Lienzo no encontrado"));
        Map<String, String> pixels = hashOps.entries(PIXELS_PREFIX + canvasId);
        return new CanvasStateDTO(pixels);
    }

    public void paintPixel(UUID canvasId, PixelDTO pixel) {
        Canvas canvas = canvasRepository.findById(canvasId)
                .orElseThrow(() -> new IllegalArgumentException("Lienzo no encontrado"));
        if (pixel.x() < 0 || pixel.x() >= canvas.getWidth()
                || pixel.y() < 0 || pixel.y() >= canvas.getHeight()) {
            throw new IllegalArgumentException("Coordenadas fuera del lienzo");
        }
        hashOps.put(PIXELS_PREFIX + canvasId, pixel.x() + "," + pixel.y(), pixel.color());
    }

    public CanvasStateDTO bulkSetPixels(UUID canvasId, String requesterId, Map<String, String> pixels) {
        Canvas canvas = canvasRepository.findById(canvasId)
                .orElseThrow(() -> new IllegalArgumentException("Lienzo no encontrado"));
        if (!canvas.isPrivate()) {
            throw new IllegalArgumentException("Solo los lienzos privados admiten plantillas generadas");
        }
        if (!requesterId.equals(canvas.getOwnerId())) {
            throw new IllegalArgumentException("Solo el dueno del lienzo puede generar una plantilla");
        }
        for (String key : pixels.keySet()) {
            String[] parts = key.split(",");
            int x = Integer.parseInt(parts[0]);
            int y = Integer.parseInt(parts[1]);
            if (x < 0 || x >= canvas.getWidth() || y < 0 || y >= canvas.getHeight()) {
                throw new IllegalArgumentException("Coordenadas fuera de los limites del lienzo");
            }
        }
        hashOps.putAll(PIXELS_PREFIX + canvasId, pixels);
        return getCanvasState(canvasId);
    }

    public void paintPixelWithCooldown(UUID canvasId, PixelPaintCommand command) {
        Canvas canvas = canvasRepository.findById(canvasId)
                .orElseThrow(() -> new IllegalArgumentException("Lienzo no encontrado"));
        if (canvas.isPrivate() && !canvasMembershipRepository.existsByCanvasIdAndUserId(canvasId, command.userId())) {
            throw new IllegalArgumentException("El usuario no pertenece a este lienzo privado");
        }
        String cooldownKey = COOLDOWN_PREFIX + canvasId + ":" + command.userId();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            Long remaining = redisTemplate.getExpire(cooldownKey, TimeUnit.MILLISECONDS);
            long millis = (remaining != null && remaining > 0) ? remaining : cooldownMillis;
            throw new CooldownActiveException(millis);
        }
        paintPixel(canvasId, new PixelDTO(command.x(), command.y(), command.color()));
        valueOps.set(cooldownKey, "1", Duration.ofMillis(cooldownMillis));
        domainEventPublisher.publishPixelPintado(canvasId, command.userId(), command.x(), command.y(), command.color());
    }
}
