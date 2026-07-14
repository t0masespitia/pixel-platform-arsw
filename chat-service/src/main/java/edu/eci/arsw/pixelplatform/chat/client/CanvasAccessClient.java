package edu.eci.arsw.pixelplatform.chat.client;

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

import java.util.Map;
import java.util.UUID;

@Component
public class CanvasAccessClient {

    private static final Logger log = LoggerFactory.getLogger(CanvasAccessClient.class);

    private final RestTemplate restTemplate;
    private final String canvasServiceBaseUrl;

    public CanvasAccessClient(RestTemplate restTemplate,
                              @Value("${canvas-service.base-url}") String canvasServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.canvasServiceBaseUrl = canvasServiceBaseUrl;
    }

    @SuppressWarnings("unchecked")
    public boolean hasAccess(UUID canvasId, String userId) {
        String url = canvasServiceBaseUrl + "/api/canvases/" + canvasId + "/access?userId=" + userId;
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                return false;
            }
            Object allowed = body.get("allowed");
            return Boolean.TRUE.equals(allowed);
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (Exception e) {
            log.warn("No se pudo verificar acceso al lienzo {} para usuario {}: {}", canvasId, userId, e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public boolean hasAccess(UUID canvasId, String userId, String bearerToken) {
        String url = canvasServiceBaseUrl + "/api/canvases/" + canvasId + "/access?userId=" + userId;
        try {
            HttpHeaders headers = new HttpHeaders();
            if (bearerToken != null) {
                headers.set("Authorization", bearerToken);
            }
            HttpEntity<?> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                return false;
            }
            return Boolean.TRUE.equals(body.get("allowed"));
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (Exception e) {
            log.warn("No se pudo verificar acceso al lienzo {} para usuario {}: {}", canvasId, userId, e.getMessage());
            return false;
        }
    }
}
