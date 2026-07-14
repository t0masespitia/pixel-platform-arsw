package edu.eci.arsw.pixelplatform.loadtest;

import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;

import java.lang.reflect.Type;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class VirtualPainter extends StompSessionHandlerAdapter {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String userId;
    private final String canvasId;
    private final int width;
    private final int height;
    private final long cooldownMillis;
    private final Metrics metrics;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Map<String, Long> pendingSends = new ConcurrentHashMap<>();
    private final CountDownLatch connectedLatch = new CountDownLatch(1);

    public VirtualPainter(String userId, String canvasId, int width, int height,
                           long cooldownMillis, Metrics metrics) {
        this.userId = userId;
        this.canvasId = canvasId;
        this.width = width;
        this.height = height;
        this.cooldownMillis = cooldownMillis;
        this.metrics = metrics;
    }

    @Override
    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
        session.subscribe("/topic/canvas/" + canvasId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return LinkedHashMap.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                onBroadcast((Map<String, Object>) payload);
            }
        });
        connectedLatch.countDown();
    }

    @Override
    public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                 byte[] payload, Throwable exception) {
        metrics.recordError();
    }

    @Override
    public void handleTransportError(StompSession session, Throwable exception) {
        metrics.recordError();
    }

    public void runPaintLoop(StompSession session, long durationMillis) throws InterruptedException {
        connectedLatch.await(5, TimeUnit.SECONDS);
        long deadline = System.currentTimeMillis() + durationMillis;
        while (running.get() && System.currentTimeMillis() < deadline) {
            int x = RANDOM.nextInt(width);
            int y = RANDOM.nextInt(height);
            String color = randomColor();
            String key = x + "," + y + "," + color;
            pendingSends.put(key, System.nanoTime());
            metrics.recordSent();
            session.send("/app/canvas/" + canvasId + "/pixel", new PixelPayload(userId, x, y, color));
            Thread.sleep(cooldownMillis + 700);
        }
    }

    public void stop() {
        running.set(false);
    }

    private void onBroadcast(Map<String, Object> pixel) {
        try {
            int x = ((Number) pixel.get("x")).intValue();
            int y = ((Number) pixel.get("y")).intValue();
            String color = (String) pixel.get("color");
            String key = x + "," + y + "," + color;
            Long sentAtNanos = pendingSends.remove(key);
            if (sentAtNanos != null) {
                long latencyMs = (System.nanoTime() - sentAtNanos) / 1_000_000;
                metrics.recordLatency(latencyMs);
            }
        } catch (Exception e) {
            metrics.recordError();
        }
    }

    private static String randomColor() {
        int value = RANDOM.nextInt(0x1000000);
        return String.format("#%06X", value);
    }
}
