package edu.eci.arsw.pixelplatform.canvas.service;

import edu.eci.arsw.pixelplatform.canvas.dto.CanvasStateDTO;
import edu.eci.arsw.pixelplatform.canvas.dto.PixelDTO;
import edu.eci.arsw.pixelplatform.canvas.dto.PixelPaintCommand;
import edu.eci.arsw.pixelplatform.canvas.exception.CooldownActiveException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CanvasStateService {

    private static final String KEY = "canvas:pixels";
    private static final String COOLDOWN_PREFIX = "canvas:cooldown:";

    private final RedisTemplate<String, String> redisTemplate;
    private final HashOperations<String, String, String> hashOps;
    private final ValueOperations<String, String> valueOps;

    @Value("${canvas.width}")
    private int canvasWidth;

    @Value("${canvas.height}")
    private int canvasHeight;

    @Value("${canvas.cooldown-seconds}")
    private int cooldownSeconds;

    public CanvasStateService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOps = redisTemplate.opsForHash();
        this.valueOps = redisTemplate.opsForValue();
    }

    public CanvasStateDTO getCanvasState() {
        Map<String, String> pixels = hashOps.entries(KEY);
        return new CanvasStateDTO(pixels);
    }

    public void paintPixel(PixelDTO pixel) {
        if (pixel.x() < 0 || pixel.x() >= canvasWidth || pixel.y() < 0 || pixel.y() >= canvasHeight) {
            throw new IllegalArgumentException("Coordenadas fuera del lienzo");
        }
        hashOps.put(KEY, pixel.x() + "," + pixel.y(), pixel.color());
    }

    public void paintPixelWithCooldown(PixelPaintCommand command) {
        String cooldownKey = COOLDOWN_PREFIX + command.userId();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            Long remaining = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
            long seconds = (remaining != null && remaining > 0) ? remaining : cooldownSeconds;
            throw new CooldownActiveException(seconds);
        }
        paintPixel(new PixelDTO(command.x(), command.y(), command.color()));
        valueOps.set(cooldownKey, "1", Duration.ofSeconds(cooldownSeconds));
    }
}
