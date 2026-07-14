package edu.eci.arsw.pixelplatform.chat.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CanvasAccessClientTest {

    @Mock
    private RestTemplate restTemplate;

    private CanvasAccessClient client;

    private static final UUID CANVAS_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String USER_ID  = "usuario1";

    @BeforeEach
    void setUp() {
        client = new CanvasAccessClient(restTemplate, "http://localhost:8082");
    }

    @Test
    void cuandoRespuestaAllowedTrue_retornaTrue() {
        ResponseEntity<Map> response = ResponseEntity.ok(Map.of("allowed", true));
        when(restTemplate.getForEntity(anyString(), eq(Map.class))).thenReturn(response);

        assertTrue(client.hasAccess(CANVAS_ID, USER_ID));
    }

    @Test
    void cuandoRespuestaAllowedFalse_retornaFalse() {
        ResponseEntity<Map> response = ResponseEntity.ok(Map.of("allowed", false));
        when(restTemplate.getForEntity(anyString(), eq(Map.class))).thenReturn(response);

        assertFalse(client.hasAccess(CANVAS_ID, USER_ID));
    }

    @Test
    void cuandoNotFound_retornaFalse() {
        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenThrow(HttpClientErrorException.NotFound.class);

        assertFalse(client.hasAccess(CANVAS_ID, USER_ID));
    }

    @Test
    void cuandoTimeout_retornaFalseSinPropagar() {
        when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        assertFalse(client.hasAccess(CANVAS_ID, USER_ID));
    }
}
