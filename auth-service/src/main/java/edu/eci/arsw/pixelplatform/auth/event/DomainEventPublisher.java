package edu.eci.arsw.pixelplatform.auth.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);
    private static final String STREAM = "auth.events";

    private final RedisTemplate<String, String> redisTemplate;

    public DomainEventPublisher(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publishUserRegistered(Long userId, String username, String email) {
        Map<String, String> fields = Map.of(
                "eventType", "USER_REGISTERED",
                "userId", userId.toString(),
                "username", username,
                "email", email,
                "timestamp", Instant.now().toString()
        );
        publish(fields);
    }

    public void publishUserAuthenticated(Long userId, String username) {
        Map<String, String> fields = Map.of(
                "eventType", "USER_AUTHENTICATED",
                "userId", userId.toString(),
                "username", username,
                "timestamp", Instant.now().toString()
        );
        publish(fields);
    }

    private void publish(Map<String, String> fields) {
        try {
            redisTemplate.opsForStream().add(MapRecord.create(STREAM, fields));
        } catch (Exception e) {
            log.warn("No se pudo publicar evento a Redis Streams: {}", e.getMessage());
        }
    }
}
