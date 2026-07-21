package edu.eci.arsw.pixelplatform.canvas.service;

import edu.eci.arsw.pixelplatform.canvas.dto.CanvasStateDTO;
import edu.eci.arsw.pixelplatform.canvas.dto.PixelDTO;
import edu.eci.arsw.pixelplatform.canvas.dto.PixelPaintCommand;
import edu.eci.arsw.pixelplatform.canvas.event.DomainEventPublisher;
import edu.eci.arsw.pixelplatform.canvas.exception.CooldownActiveException;
import edu.eci.arsw.pixelplatform.canvas.model.Canvas;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasMembershipRepository;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasRepository;
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
import java.util.Optional;
import java.util.UUID;
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

    @Mock
    private CanvasRepository canvasRepository;

    @Mock
    private CanvasMembershipRepository canvasMembershipRepository;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    private CanvasStateService service;

    private static final UUID TEST_CANVAS_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String PIXELS_KEY   = "canvas:pixels:" + TEST_CANVAS_ID;
    private static final String COOLDOWN_KEY = "canvas:cooldown:" + TEST_CANVAS_ID + ":usuario123";

    @BeforeEach
    void setUp() {
        doReturn(hashOperations).when(redisTemplate).opsForHash();
        doReturn(valueOperations).when(redisTemplate).opsForValue();
        service = new CanvasStateService(redisTemplate, canvasRepository, canvasMembershipRepository, domainEventPublisher);
        ReflectionTestUtils.setField(service, "cooldownMillis", 5000L);

        Canvas testCanvas = new Canvas();
        testCanvas.setId(TEST_CANVAS_ID);
        testCanvas.setWidth(100);
        testCanvas.setHeight(100);
        testCanvas.setPrivate(false);
        lenient().when(canvasRepository.findById(TEST_CANVAS_ID)).thenReturn(Optional.of(testCanvas));
    }

    @Test
    void pintarPixelValido_debeLlamarHSet() {
        service.paintPixel(TEST_CANVAS_ID, new PixelDTO(5, 10, "#FF5733"));

        verify(hashOperations).put(PIXELS_KEY, "5,10", "#FF5733");
    }

    @Test
    void pintarPixelFueraDeLimites_debeLanzarExcepcion() {
        assertThrows(IllegalArgumentException.class, () ->
                service.paintPixel(TEST_CANVAS_ID, new PixelDTO(150, 10, "#FF5733"))
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
        when(hashOperations.entries(PIXELS_KEY)).thenReturn(fakeEntries);

        CanvasStateDTO result = service.getCanvasState(TEST_CANVAS_ID);

        assertEquals(3, result.pixels().size());
        assertEquals("#FF5733", result.pixels().get("5,10"));
        assertEquals("#FFFFFF", result.pixels().get("0,0"));
    }

    @Test
    void pintarConCooldownInactivo_debePintarYEstablecerCooldown() {
        when(redisTemplate.hasKey(COOLDOWN_KEY)).thenReturn(false);

        service.paintPixelWithCooldown(TEST_CANVAS_ID, new PixelPaintCommand("usuario123", 5, 10, "#FF5733"));

        verify(hashOperations).put(PIXELS_KEY, "5,10", "#FF5733");
        verify(valueOperations).set(COOLDOWN_KEY, "1", Duration.ofMillis(5000));
        verify(domainEventPublisher).publishPixelPintado(TEST_CANVAS_ID, "usuario123", 5, 10, "#FF5733");
    }

    @Test
    void pintarConCooldownActivo_debeLanzarCooldownActiveException() {
        when(redisTemplate.hasKey(COOLDOWN_KEY)).thenReturn(true);
        when(redisTemplate.getExpire(COOLDOWN_KEY, TimeUnit.MILLISECONDS)).thenReturn(3000L);

        CooldownActiveException ex = assertThrows(CooldownActiveException.class, () ->
                service.paintPixelWithCooldown(TEST_CANVAS_ID, new PixelPaintCommand("usuario123", 5, 10, "#FF5733"))
        );

        assertEquals(3000L, ex.getRemainingMillis());
        verify(hashOperations, never()).put(any(), any(), any());
        verify(domainEventPublisher, never()).publishPixelPintado(any(UUID.class), any(), anyInt(), anyInt(), any());
    }

    @Test
    void bulkSetPixels_lienzoPublico_debeLanzarExcepcion() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.bulkSetPixels(TEST_CANVAS_ID, "owner1", Map.of("0,0", "#FFFFFF"))
        );
        assertEquals("Solo los lienzos privados admiten plantillas generadas", ex.getMessage());
        verify(hashOperations, never()).putAll(any(), any());
    }

    @Test
    void bulkSetPixels_requesterDistintoAlOwner_debeLanzarExcepcion() {
        UUID privateId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        Canvas privateCanvas = new Canvas();
        privateCanvas.setId(privateId);
        privateCanvas.setWidth(50);
        privateCanvas.setHeight(50);
        privateCanvas.setPrivate(true);
        privateCanvas.setOwnerId("owner1");
        when(canvasRepository.findById(privateId)).thenReturn(Optional.of(privateCanvas));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.bulkSetPixels(privateId, "intruso", Map.of("0,0", "#FFFFFF"))
        );
        assertEquals("Solo el dueno del lienzo puede generar una plantilla", ex.getMessage());
        verify(hashOperations, never()).putAll(any(), any());
    }

    @Test
    void bulkSetPixels_coordenadaFueraDeRango_debeLanzarExcepcion() {
        UUID privateId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        Canvas privateCanvas = new Canvas();
        privateCanvas.setId(privateId);
        privateCanvas.setWidth(10);
        privateCanvas.setHeight(10);
        privateCanvas.setPrivate(true);
        privateCanvas.setOwnerId("owner1");
        when(canvasRepository.findById(privateId)).thenReturn(Optional.of(privateCanvas));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.bulkSetPixels(privateId, "owner1", Map.of("10,0", "#FFFFFF"))
        );
        assertEquals("Coordenadas fuera de los limites del lienzo", ex.getMessage());
        verify(hashOperations, never()).putAll(any(), any());
    }

    @Test
    void bulkSetPixels_casoExitoso_debeEscribirPutAllYRetornarSnapshot() {
        UUID privateId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        Canvas privateCanvas = new Canvas();
        privateCanvas.setId(privateId);
        privateCanvas.setWidth(10);
        privateCanvas.setHeight(10);
        privateCanvas.setPrivate(true);
        privateCanvas.setOwnerId("owner1");
        when(canvasRepository.findById(privateId)).thenReturn(Optional.of(privateCanvas));

        Map<String, String> pixels = Map.of("0,0", "#FF0000", "9,9", "#00FF00");
        String pixelsKey = "canvas:pixels:" + privateId;
        when(hashOperations.entries(pixelsKey)).thenReturn(pixels);

        CanvasStateDTO result = service.bulkSetPixels(privateId, "owner1", pixels);

        verify(hashOperations).putAll(pixelsKey, pixels);
        assertEquals(pixels, result.pixels());
    }

    @Test
    void dosPinturasSobreElMismoLienzo_soloDebeConsultarPostgresUnaVez() {
        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        service.paintPixelWithCooldown(TEST_CANVAS_ID, new PixelPaintCommand("usuario123", 1, 1, "#FFFFFF"));
        service.paintPixelWithCooldown(TEST_CANVAS_ID, new PixelPaintCommand("usuario123", 2, 2, "#FFFFFF"));

        verify(canvasRepository, times(1)).findById(TEST_CANVAS_ID);
    }

    @Test
    void pintarEnLienzoPrivadoSinMembresia_debeLanzarExcepcion() {
        UUID privateCanvasId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        Canvas privateCanvas = new Canvas();
        privateCanvas.setId(privateCanvasId);
        privateCanvas.setWidth(100);
        privateCanvas.setHeight(100);
        privateCanvas.setPrivate(true);
        when(canvasRepository.findById(privateCanvasId)).thenReturn(Optional.of(privateCanvas));
        when(canvasMembershipRepository.existsByCanvasIdAndUserId(privateCanvasId, "usuario123")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                service.paintPixelWithCooldown(privateCanvasId, new PixelPaintCommand("usuario123", 5, 10, "#FF5733"))
        );

        assertEquals("El usuario no pertenece a este lienzo privado", ex.getMessage());
        verify(hashOperations, never()).put(any(), any(), any());
    }
}
