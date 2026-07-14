package edu.eci.arsw.pixelplatform.gateway.filter;

import edu.eci.arsw.pixelplatform.gateway.security.JwtVerifier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/verify-email",
            "/api/auth/resend-code",
            "/api/auth/users/lookup",
            "/api/auth/users"
    );

    private final JwtVerifier jwtVerifier;

    public JwtAuthGlobalFilter(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();
        if (PUBLIC_PATHS.contains(path) || path.startsWith("/api/auth/avatars/") ||
                "OPTIONS".equalsIgnoreCase(method)) {
            return chain.filter(exchange);
        }
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        try {
            jwtVerifier.verifyAndExtractUserId(header);
            return chain.filter(exchange);
        } catch (IllegalArgumentException e) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().add("Content-Type", "application/json");
            byte[] bytes = ("{\"error\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
