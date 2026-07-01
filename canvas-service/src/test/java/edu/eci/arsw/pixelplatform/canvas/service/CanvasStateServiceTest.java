package edu.eci.arsw.pixelplatform.canvas.service;

import edu.eci.arsw.pixelplatform.canvas.dto.CanvasStateDTO;
import edu.eci.arsw.pixelplatform.canvas.dto.PixelDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CanvasStateServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private HashOperations<String, String, String> hashOperations;

    private CanvasStateService service;

    private static final String KEY = "canvas:pixels";

    @BeforeEach
    void setUp() {
        doReturn(hashOperations).when(redisTemplate).opsForHash();
        service = new CanvasStateService(redisTemplate);
        ReflectionTestUtils.setField(service, "canvasWidth", 100);
        ReflectionTestUtils.setField(service, "canvasHeight", 100);
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
}
