package edu.eci.arsw.pixelplatform.canvas.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Publica y lee eventos de dominio del lienzo usando Redis Streams. Cada
 * lienzo tiene su propio stream ("canvas.events:{canvasId}") para poder leer
 * su historial de forma directa y eficiente con XRANGE, sin tener que filtrar
 * un stream global compartido por todos los lienzos.
 *
 * El stream se recorta (trim) a MAX_EVENTS_PER_CANVAS despues de cada
 * escritura, para que no crezca sin limite en memoria — esto es un historial
 * "reciente" para replay, no un log de auditoria permanente (para eso haria
 * falta persistirlo en una base de datos durable, fuera de alcance por ahora).
 */
@Service
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);
    private static final String STREAM_PREFIX = "canvas.events:";
    private static final long MAX_EVENTS_PER_CANVAS = 5000;

    private final RedisTemplate<String, String> redisTemplate;

    public DomainEventPublisher(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publishPixelPintado(UUID canvasId, String userId, int x, int y, String color) {
        Map<String, String> fields = Map.of(
                "eventType", "PIXEL_PINTADO",
                "canvasId", canvasId.toString(),
                "userId", userId,
                "x", String.valueOf(x),
                "y", String.valueOf(y),
                "color", color,
                "timestamp", Instant.now().toString()
        );
        publish(canvasId, fields);
    }

    /**
     * Devuelve los ultimos "limit" eventos del lienzo, en orden cronologico
     * (mas viejo primero), listos para reproducir paso a paso en el frontend.
     */
    public List<Map<String, String>> getHistory(UUID canvasId, int limit) {
        String streamKey = STREAM_PREFIX + canvasId;
        List<MapRecord<String, Object, Object>> records;
        try {
            records = redisTemplate.opsForStream().range(streamKey, Range.unbounded());
        } catch (Exception e) {
            log.warn("No se pudo leer el historial de eventos de canvasId={}: {}", canvasId, e.getMessage());
            return List.of();
        }
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.max(0, records.size() - limit);
        List<Map<String, String>> result = new ArrayList<>();
        for (MapRecord<String, Object, Object> record : records.subList(fromIndex, records.size())) {
            Map<String, String> fields = new LinkedHashMap<>();
            record.getValue().forEach((key, value) -> fields.put(String.valueOf(key), String.valueOf(value)));
            fields.put("eventId", record.getId().getValue());
            result.add(fields);
        }
        return result;
    }

    private void publish(UUID canvasId, Map<String, String> fields) {
        String streamKey = STREAM_PREFIX + canvasId;
        try {
            redisTemplate.opsForStream().add(MapRecord.create(streamKey, fields));
            redisTemplate.opsForStream().trim(streamKey, MAX_EVENTS_PER_CANVAS);
        } catch (Exception e) {
            log.warn("No se pudo publicar evento a Redis Streams: {}", e.getMessage());
        }
    }
}
