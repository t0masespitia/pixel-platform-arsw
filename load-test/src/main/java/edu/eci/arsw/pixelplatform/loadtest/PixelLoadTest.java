package edu.eci.arsw.pixelplatform.loadtest;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.SecretKey;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Prueba de carga del WebSocket de pintado de PixelPlatform (canvas-service).
 *
 * Uso:
 *   java -jar target/load-test.jar [baseUrl] [canvasId] [users] [durationSeconds] [width] [height] [cooldownMillis] [jwtSecret]
 */
public class PixelLoadTest {

    public static void main(String[] args) throws Exception {
        String baseUrl        = arg(args, 0, "http://localhost:8082");
        String canvasId       = arg(args, 1, "00000000-0000-0000-0000-000000000001");
        int users             = Integer.parseInt(arg(args, 2, "20"));
        int durationSeconds   = Integer.parseInt(arg(args, 3, "30"));
        int width             = Integer.parseInt(arg(args, 4, "100"));
        int height            = Integer.parseInt(arg(args, 5, "200"));
        long cooldownMillis   = Long.parseLong(arg(args, 6, "500"));
        String jwtSecret      = arg(args, 7, "dev-secret-key-pixelplatform-auth-service-arsw-2026");
        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        System.out.printf("Prueba de carga PixelPlatform: %d usuarios, %ds, canvas=%s, %s%n",
                users, durationSeconds, canvasId, baseUrl);

        int[] realDimensions = fetchCanvasDimensions(baseUrl, canvasId, new int[]{width, height},
                mintToken("loadtest-dimensions", signingKey));
        width = realDimensions[0];
        height = realDimensions[1];

        Metrics metrics = new Metrics();
        WebSocketStompClient stompClient = buildStompClient();

        List<VirtualPainter> painters = new ArrayList<>();
        List<StompSession> sessions = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < users; i++) {
            String userId = "loadtest-user-" + i;
            String token = mintToken(userId, signingKey);
            VirtualPainter painter = new VirtualPainter(userId, canvasId, width, height, cooldownMillis, metrics);

            StompHeaders connectHeaders = new StompHeaders();
            connectHeaders.add("Authorization", "Bearer " + token);

            try {
                StompSession session = stompClient
                        .connectAsync(baseUrl + "/ws-canvas", (WebSocketHttpHeaders) null, connectHeaders, painter)
                        .get(10, TimeUnit.SECONDS);
                sessions.add(session);
                painters.add(painter);
            } catch (Exception e) {
                System.err.println("No se pudo conectar userId=" + userId + ": " + e.getMessage());
                metrics.recordError();
            }
        }

        System.out.printf("%d/%d usuarios conectados. Pintando durante %ds...%n",
                sessions.size(), users, durationSeconds);

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < sessions.size(); i++) {
            StompSession session = sessions.get(i);
            VirtualPainter painter = painters.get(i);
            Thread t = new Thread(() -> {
                try {
                    painter.runPaintLoop(session, durationSeconds * 1000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }, "painter-" + i);
            workers.add(t);
            t.start();
        }

        for (Thread t : workers) {
            t.join();
        }

        // Ventana de gracia para que lleguen los ultimos broadcasts en vuelo.
        Thread.sleep(2000);

        for (StompSession session : sessions) {
            session.disconnect();
        }

        printReport(metrics, sessions.size(), users);
    }

    private static WebSocketStompClient buildStompClient() {
        StandardWebSocketClient webSocketClient = new StandardWebSocketClient();
        List<Transport> transports = new ArrayList<>();
        transports.add(new WebSocketTransport(webSocketClient));
        transports.add(new RestTemplateXhrTransport());
        SockJsClient sockJsClient = new SockJsClient(transports);
        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        return stompClient;
    }

    private static String mintToken(String userId, SecretKey signingKey) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + 3_600_000L);
        return Jwts.builder()
                .subject(userId + "@loadtest.local")
                .claim("username", userId)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    private static String arg(String[] args, int index, String defaultValue) {
        return (args.length > index) ? args[index] : defaultValue;
    }

    /**
     * Consulta a canvas-service las dimensiones reales del lienzo antes de
     * arrancar la prueba. Este endpoint no requiere autenticacion al llamarlo
     * directo (sin pasar por el gateway), igual que ya hace la conexion
     * WebSocket. Si la consulta falla por cualquier motivo, usa el fallback
     * (los valores de width/height pasados por linea de comandos) para no
     * bloquear la prueba.
     */
    private static int[] fetchCanvasDimensions(String baseUrl, String canvasId, int[] fallback, String token) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/canvases/" + canvasId))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.err.printf("No se pudo obtener el lienzo (HTTP %d), usando %dx%d por defecto%n",
                        response.statusCode(), fallback[0], fallback[1]);
                return fallback;
            }
            JsonNode node = new ObjectMapper().readTree(response.body());
            int width = node.get("width").asInt();
            int height = node.get("height").asInt();
            System.out.printf("Dimensiones reales del lienzo obtenidas del servidor: %dx%d%n", width, height);
            return new int[]{width, height};
        } catch (Exception e) {
            System.err.printf("No se pudo consultar canvas-service (%s), usando %dx%d por defecto%n",
                    e.getMessage(), fallback[0], fallback[1]);
            return fallback;
        }
    }

    private static void printReport(Metrics metrics, int connectedUsers, int requestedUsers) {
        List<Long> latencies = metrics.getLatencies();
        Collections.sort(latencies);
        long sent     = metrics.getSent();
        long received = latencies.size();
        long errors   = metrics.getErrors();

        System.out.println();
        System.out.println("========== Reporte de prueba de carga ==========");
        System.out.printf("Usuarios conectados          : %d/%d%n", connectedUsers, requestedUsers);
        System.out.printf("Pixeles enviados             : %d%n", sent);
        System.out.printf("Confirmaciones recibidas     : %d%n", received);
        System.out.printf("Sin confirmacion             : %d%n", sent - received);
        System.out.printf("Errores de conexion/STOMP    : %d%n", errors);
        if (!latencies.isEmpty()) {
            System.out.printf("Latencia p50                 : %d ms%n", percentile(latencies, 50));
            System.out.printf("Latencia p95                 : %d ms%n", percentile(latencies, 95));
            System.out.printf("Latencia p99                 : %d ms%n", percentile(latencies, 99));
            System.out.printf("Latencia maxima              : %d ms%n", latencies.get(latencies.size() - 1));
            double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
            System.out.printf("Latencia promedio            : %.1f ms%n", avg);
        }
        System.out.println("==================================================");
    }

    private static long percentile(List<Long> sortedLatencies, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sortedLatencies.size()) - 1;
        index = Math.max(0, Math.min(index, sortedLatencies.size() - 1));
        return sortedLatencies.get(index);
    }
}
