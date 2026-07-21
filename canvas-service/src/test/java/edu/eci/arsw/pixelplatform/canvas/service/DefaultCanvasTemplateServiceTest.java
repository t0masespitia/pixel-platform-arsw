package edu.eci.arsw.pixelplatform.canvas.service;

import edu.eci.arsw.pixelplatform.canvas.dto.CanvasResponse;
import edu.eci.arsw.pixelplatform.canvas.model.Canvas;
import edu.eci.arsw.pixelplatform.canvas.model.CanvasMembership;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasMembershipRepository;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultCanvasTemplateServiceTest {

    @Mock
    private CanvasRepository canvasRepository;

    @Mock
    private CanvasMembershipRepository canvasMembershipRepository;

    @Mock
    private CanvasStateService canvasStateService;

    private DefaultCanvasTemplateService service;

    @BeforeEach
    void setUp() {
        service = new DefaultCanvasTemplateService(
                canvasRepository, canvasMembershipRepository, canvasStateService);
        ReflectionTestUtils.setField(service, "templateWidth", 64);
        ReflectionTestUtils.setField(service, "templateHeight", 64);
    }

    @Test
    void createDefaultCanvasesForUserNuevoCreaCuatroLienzosMembresiasYPixeles() {
        when(canvasRepository.findByOwnerId("owner-1")).thenReturn(List.of());
        when(canvasRepository.save(any(Canvas.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(canvasMembershipRepository.save(any(CanvasMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var creados = service.createDefaultCanvasesForUser("owner-1");

        assertThat(creados).hasSize(4);
        assertThat(creados).extracting(CanvasResponse::name)
                .containsExactly("pinta a stitch", "Pinta a pikachu", "pinta a un perrito",
                        "pinta al pajaro carpintero");

        ArgumentCaptor<Canvas> canvasCaptor = ArgumentCaptor.forClass(Canvas.class);
        verify(canvasRepository, org.mockito.Mockito.times(4)).save(canvasCaptor.capture());
        assertThat(canvasCaptor.getAllValues())
                .allSatisfy(canvas -> {
                    assertThat(canvas.isDefaultTemplate()).isTrue();
                    assertThat(canvas.isPrivate()).isTrue();
                    assertThat(canvas.getOwnerId()).isEqualTo("owner-1");
                    assertThat(canvas.getWidth()).isEqualTo(64);
                    assertThat(canvas.getHeight()).isEqualTo(64);
                });

        verify(canvasMembershipRepository, org.mockito.Mockito.times(4)).save(any(CanvasMembership.class));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> pixelsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(canvasStateService, org.mockito.Mockito.times(4))
                .bulkSetPixels(any(UUID.class), eq("owner-1"), pixelsCaptor.capture());
        assertThat(pixelsCaptor.getAllValues()).allSatisfy(pixels -> {
            assertThat(pixels).isNotEmpty();
            assertThat(pixels.values()).allSatisfy(DefaultCanvasTemplateServiceTest::assertGrayscaleColor);
        });
    }

    @Test
    void siYaTieneCuatroPredeterminadosNoCreaNadaNuevo() {
        List<Canvas> existentes = List.of(
                existingDefaultCanvas("owner-2", 1),
                existingDefaultCanvas("owner-2", 2),
                existingDefaultCanvas("owner-2", 3),
                existingDefaultCanvas("owner-2", 4)
        );
        when(canvasRepository.findByOwnerId("owner-2")).thenReturn(existentes);

        var response = service.createDefaultCanvasesForUser("owner-2");

        assertThat(response).hasSize(4);
        verify(canvasRepository, never()).save(any(Canvas.class));
        verify(canvasMembershipRepository, never()).save(any(CanvasMembership.class));
        verify(canvasStateService, never()).bulkSetPixels(any(UUID.class), any(String.class), any());
    }

    @Test
    void siUnaIteracionFallaLasOtrasTresIgualSeCrean() {
        when(canvasRepository.findByOwnerId("owner-3")).thenReturn(List.of());
        when(canvasRepository.save(any(Canvas.class)))
                .thenThrow(new RuntimeException("fallo simulado"))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(canvasMembershipRepository.save(any(CanvasMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var creados = service.createDefaultCanvasesForUser("owner-3");

        assertThat(creados).hasSize(3);
        verify(canvasRepository, org.mockito.Mockito.times(4)).save(any(Canvas.class));
        verify(canvasMembershipRepository, org.mockito.Mockito.times(3)).save(any(CanvasMembership.class));
        verify(canvasStateService, org.mockito.Mockito.times(3))
                .bulkSetPixels(any(UUID.class), eq("owner-3"), any());
    }

    private Canvas existingDefaultCanvas(String ownerId, int index) {
        return Canvas.builder()
                .id(UUID.randomUUID())
                .name("Plantilla existente " + index)
                .ownerId(ownerId)
                .width(64)
                .height(64)
                .isPrivate(true)
                .isDefaultTemplate(true)
                .createdAt(Instant.now())
                .build();
    }

    private static void assertGrayscaleColor(String color) {
        assertThat(color).matches("#[0-9A-F]{6}");
        assertThat(color.substring(1, 3)).isEqualTo(color.substring(3, 5));
        assertThat(color.substring(3, 5)).isEqualTo(color.substring(5, 7));
    }
}
