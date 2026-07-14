package edu.eci.arsw.pixelplatform.gateway.ratelimit;

import edu.eci.arsw.pixelplatform.gateway.security.JwtVerifier;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Resuelve la clave que usa el RequestRateLimiter para contar peticiones.
 * Si la peticion trae un JWT valido, el limite se aplica por usuario (userId),
 * para que un usuario no afecte la cuota de otro. Si no trae un JWT valido
 * (login, registro, endpoints publicos), se aplica por IP del cliente, para
 * que un solo cliente anonimo no pueda saltarse el limite simplemente sin
 * loguearse ni agotar la cuota compartida de todos los demas.
 */
@Component("userKeyResolver")
public class UserKeyResolver implements KeyResolver {

    private final JwtVerifier jwtVerifier;

    public UserKeyResolver(JwtVerifier jwtVerifier) {
        this.jwtVerifier = jwtVerifier;
    }

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String header = exchange.getRequest().getHeaders().getFirst("Authorization");
        try {
            String userId = jwtVerifier.verifyAndExtractUserId(header);
            return Mono.just("user:" + userId);
        } catch (IllegalArgumentException e) {
            return Mono.just("ip:" + resolveClientIp(exchange));
        }
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return "unknown";
        }
        return remoteAddress.getAddress().getHostAddress();
    }
}
