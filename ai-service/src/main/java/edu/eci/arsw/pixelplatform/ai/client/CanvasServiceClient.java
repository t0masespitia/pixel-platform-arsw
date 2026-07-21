package edu.eci.arsw.pixelplatform.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Component
public class CanvasServiceClient {

    private static final Logger log = LoggerFactory.getLogger(CanvasServiceClient.class);

    public record CanvasInfo(UUID id, String name, String ownerId, int width, int height,
                              boolean isPrivate, boolean isDefaultTemplate) {}

    private final RestTemplate restTemplate;
    private final String canvasServiceBaseUrl;
    private final ObjectMapper objectMapper;

    public CanvasServiceClient(RestTemplate restTemplate,
                               @Value("${canvas-service.base-url}") String canvasServiceBaseUrl,
                               ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.canvasServiceBaseUrl = canvasServiceBaseUrl;
        this.objectMapper = objectMapper;
    }

    @CircuitBreaker(name = "canvasService", fallbackMethod = "getCanvasFallback")
    @Retry(name = "canvasService")
    public CanvasInfo getCanvas(UUID canvasId, String bearerToken) {
        String url = canvasServiceBaseUrl + "/api/canvases/" + canvasId;
        try {
            HttpHeaders headers = new HttpHeaders();
            if (bearerToken != null) {
                headers.set("Authorization", bearerToken);
            }
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<CanvasInfo> response = restTemplate.exchange(url, HttpMethod.GET, entity, CanvasInfo.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("Lienzo no encontrado");
        } catch (Exception e) {
            throw new RuntimeException("No se pudo contactar canvas-service");
        }
    }

    private CanvasInfo getCanvasFallback(UUID canvasId, String bearerToken, Throwable t) {
        log.warn("Circuit breaker 'canvasService' activo en getCanvas (canvasId={}): {}", canvasId, t.getMessage());
        throw new RuntimeException("No se pudo contactar canvas-service");
    }

    @SuppressWarnings("unchecked")
    @CircuitBreaker(name = "canvasService", fallbackMethod = "bulkSetPixelsFallback")
    @Retry(name = "canvasService")
    public void bulkSetPixels(UUID canvasId, String requesterId, Map<String, String> pixels, String bearerToken) {
        String url = canvasServiceBaseUrl + "/api/canvas/" + canvasId + "/pixels/bulk";
        Map<String, Object> body = Map.of("requesterId", requesterId, "pixels", pixels);
        try {
            HttpHeaders headers = new HttpHeaders();
            if (bearerToken != null) {
                headers.set("Authorization", bearerToken);
            }
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, entity, Void.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 400) {
                try {
                    Map<String, String> errorBody = objectMapper.readValue(
                            e.getResponseBodyAsString(), Map.class);
                    throw new IllegalArgumentException(
                            errorBody.getOrDefault("error", "Error al escribir pixels"));
                } catch (IOException ex) {
                    throw new IllegalArgumentException("Error al escribir pixels");
                }
            }
            throw new RuntimeException("No se pudo contactar canvas-service");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo contactar canvas-service");
        }
    }

    private void bulkSetPixelsFallback(UUID canvasId, String requesterId, Map<String, String> pixels,
            String bearerToken, Throwable t) {
        log.warn("Circuit breaker 'canvasService' activo en bulkSetPixels (canvasId={}): {}", canvasId, t.getMessage());
        throw new RuntimeException("No se pudo contactar canvas-service");
    }
}
