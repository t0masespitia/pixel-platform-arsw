package edu.eci.arsw.pixelplatform.canvas.service;

import edu.eci.arsw.pixelplatform.canvas.dto.CanvasStateDTO;
import edu.eci.arsw.pixelplatform.canvas.dto.PixelDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CanvasStateService {

    private static final String KEY = "canvas:pixels";

    private final HashOperations<String, String, String> hashOps;

    @Value("${canvas.width}")
    private int canvasWidth;

    @Value("${canvas.height}")
    private int canvasHeight;

    public CanvasStateService(RedisTemplate<String, String> redisTemplate) {
        this.hashOps = redisTemplate.opsForHash();
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
}
