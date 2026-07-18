package edu.eci.arsw.pixelplatform.canvas.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Component
public class ChatServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceClient.class);

    private final RestTemplate restTemplate;
    private final String chatServiceBaseUrl;

    public ChatServiceClient(RestTemplate restTemplate,
                             @Value("${chat-service.base-url}") String chatServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.chatServiceBaseUrl = chatServiceBaseUrl;
    }

    @CircuitBreaker(name = "chatService", fallbackMethod = "sendInvitationMessageFallback")
    @Retry(name = "chatService")
    public void sendInvitationMessage(String authHeader, String toUserId, UUID canvasId,
            String canvasName, UUID invitationId, String content) {
        String url = chatServiceBaseUrl + "/api/messages/canvas-invitation";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                "toUserId", toUserId,
                "canvasId", canvasId.toString(),
                "canvasName", canvasName,
                "invitationId", invitationId.toString(),
                "content", content
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        restTemplate.postForEntity(url, entity, Void.class);
    }

    private void sendInvitationMessageFallback(String authHeader, String toUserId, UUID canvasId,
            String canvasName, UUID invitationId, String content, Throwable t) {
        log.warn("Circuit breaker 'chatService' activo en sendInvitationMessage (toUserId={}): {}",
                toUserId, t.getMessage());
        throw new RuntimeException("chat-service no disponible: " + t.getMessage(), t);
    }

    @CircuitBreaker(name = "chatService", fallbackMethod = "deleteInvitationMessagesFallback")
    @Retry(name = "chatService")
    public void deleteInvitationMessages(String authHeader, UUID invitationId) {
        String url = chatServiceBaseUrl + "/api/messages/canvas-invitation/" + invitationId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", authHeader);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        restTemplate.exchange(url, HttpMethod.DELETE, entity, Void.class);
    }

    private void deleteInvitationMessagesFallback(String authHeader, UUID invitationId, Throwable t) {
        log.warn("Circuit breaker 'chatService' activo en deleteInvitationMessages (invitationId={}): {}",
                invitationId, t.getMessage());
        throw new RuntimeException("chat-service no disponible: " + t.getMessage(), t);
    }
}
