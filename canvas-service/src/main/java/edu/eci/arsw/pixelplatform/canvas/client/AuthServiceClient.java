package edu.eci.arsw.pixelplatform.canvas.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Optional;

@Component
public class AuthServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceClient.class);

    private final RestTemplate restTemplate;
    private final String authServiceBaseUrl;

    public AuthServiceClient(RestTemplate restTemplate,
                             @Value("${auth-service.base-url}") String authServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.authServiceBaseUrl = authServiceBaseUrl;
    }

    @CircuitBreaker(name = "authService", fallbackMethod = "findUserIdByEmailFallback")
    @Retry(name = "authService")
    @SuppressWarnings("unchecked")
    public Optional<String> findUserIdByEmail(String email) {
        String url = authServiceBaseUrl + "/api/auth/users/lookup?email=" + email;
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null || !body.containsKey("userId")) {
                return Optional.empty();
            }
            return Optional.of(String.valueOf(body.get("userId")));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo contactar auth-service: " + e.getMessage(), e);
        }
    }

    private Optional<String> findUserIdByEmailFallback(String email, Throwable t) {
        log.warn("Circuit breaker 'authService' activo en findUserIdByEmail (email={}): {}", email, t.getMessage());
        throw new RuntimeException("auth-service no disponible: " + t.getMessage(), t);
    }

    @CircuitBreaker(name = "authService", fallbackMethod = "getUserProfileFallback")
    @Retry(name = "authService")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getUserProfile(String authHeader, String userId) {
        String url = authServiceBaseUrl + "/api/auth/users/" + userId;
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", authHeader);
        org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET,
                entity, Map.class);
        return response.getBody();
    }

    private Map<String, Object> getUserProfileFallback(String authHeader, String userId, Throwable t) {
        log.warn("Circuit breaker 'authService' activo en getUserProfile (userId={}): {}", userId, t.getMessage());
        throw new RuntimeException("auth-service no disponible: " + t.getMessage(), t);
    }

    @CircuitBreaker(name = "authService", fallbackMethod = "sendInvitationEmailFallback")
    @Retry(name = "authService")
    public void sendInvitationEmail(String authHeader, String targetUserId, String inviterName,
            String canvasName, String code, String joinLink) {
        String url = authServiceBaseUrl + "/api/auth/notifications/send-invitation-email";
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", authHeader);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of(
                "targetUserId", targetUserId,
                "inviterName", inviterName,
                "canvasName", canvasName,
                "code", code,
                "joinLink", joinLink
        );
        org.springframework.http.HttpEntity<Map<String, String>> entity =
                new org.springframework.http.HttpEntity<>(body, headers);
        restTemplate.postForEntity(url, entity, Void.class);
    }

    private void sendInvitationEmailFallback(String authHeader, String targetUserId, String inviterName,
            String canvasName, String code, String joinLink, Throwable t) {
        log.warn("Circuit breaker 'authService' activo en sendInvitationEmail (targetUserId={}): {}",
                targetUserId, t.getMessage());
        throw new RuntimeException("auth-service no disponible: " + t.getMessage(), t);
    }
}
