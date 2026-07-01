package edu.eci.arsw.pixelplatform.canvas.service;

import edu.eci.arsw.pixelplatform.canvas.dto.CanvasStateDTO;
import edu.eci.arsw.pixelplatform.canvas.dto.PixelDTO;
import edu.eci.arsw.pixelplatform.canvas.dto.PixelPaintCommand;
import edu.eci.arsw.pixelplatform.canvas.exception.CooldownActiveException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CanvasStateServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private HashOperations<String, String, String> hashOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private CanvasStateService service;

    private static final String KEY = "canvas:pixels";
    private static final String COOLDOWN_KEY = "canvas:cooldown:usuario123";

    @BeforeEach
    void setUp() {
        doReturn(hashOperations).when(redisTemplate).opsForHash();
        doReturn(valueOperations).when(redisTemplate).opsForValue();
        service = new CanvasStateService(redisTemplate);
        ReflectionTestUtils.setField(service, "canvasWidth", 100);
        ReflectionTestUtils.setField(service, "canvasHeight", 100);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 5);
    }

    @Test
    void pintarPixelValido_debeLlamarHSet() {
        service.paintPixel(new PixelDTO(5, 10, "#FF5733"));

        verify(hashOperations).put(KEY, "5,10", "#FF5733");
    }

    @Test
    void pintarPixelFueraDeLimites_debeLanzarExcepcion() {
        assertThrows(IllegalArgumentException.class, () ->
                service.paintPixel(new PixelDTO(150, 10, "#FF5733"))
        );
        verify(hashOperations, never()).put(any(), any(), any());
    }

    @Test
    void obtenerEstadoDelLienzo_debeRetornarMapaCompleto() {
        Map<String, String> fakeEntries = Map.of(
                "0,0", "#FFFFFF",
                "5,10", "#FF5733",
                "99,99", "#000000"
        );
        when(hashOperations.entries(KEY)).thenReturn(fakeEntries);

        CanvasStateDTO result = service.getCanvasState();

        assertEquals(3, result.pixels().size());
        assertEquals("#FF5733", result.pixels().get("5,10"));
        assertEquals("#FFFFFF", result.pixels().get("0,0"));
    }

    @Test
    void pintarConCooldownInactivo_debePintarYEstablecerCooldown() {
        when(redisTemplate.hasKey(COOLDOWN_KEY)).thenReturn(false);

        service.paintPixelWithCooldown(new PixelPaintCommand("usuario123", 5, 10, "#FF5733"));

        verify(hashOperations).put(KEY, "5,10", "#FF5733");
        verify(valueOperations).set(COOLDOWN_KEY, "1", Duration.ofSeconds(5));
    }

    @Test
    void pintarConCooldownActivo_debeLanzarCooldownActiveException() {
        when(redisTemplate.hasKey(COOLDOWN_KEY)).thenReturn(true);
        when(redisTemplate.getExpire(COOLDOWN_KEY, TimeUnit.SECONDS)).thenReturn(3L);

        CooldownActiveException ex = assertThrows(CooldownActiveException.class, () ->
                service.paintPixelWithCooldown(new PixelPaintCommand("usuario123", 5, 10, "#FF5733"))
        );

        assertEquals(3L, ex.getRemainingSeconds());
        verify(hashOperations, never()).put(any(), any(), any());
    }
}
