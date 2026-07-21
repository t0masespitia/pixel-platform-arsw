package edu.eci.arsw.pixelplatform.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class CanvasServiceClient {

    private final RestTemplate restTemplate;
    private final String canvasServiceBaseUrl;

    public CanvasServiceClient(RestTemplate restTemplate,
                               @Value("${canvas-service.base-url}") String canvasServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.canvasServiceBaseUrl = canvasServiceBaseUrl;
    }

    public void createDefaultCanvases(String userId, String bearerToken) {
        String url = canvasServiceBaseUrl + "/api/canvases/default-templates";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", bearerToken);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(Map.of("ownerId", userId), headers);
        restTemplate.postForEntity(url, entity, Void.class);
    }
}
