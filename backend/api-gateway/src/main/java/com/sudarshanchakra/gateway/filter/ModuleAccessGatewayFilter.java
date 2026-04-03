package com.sudarshanchakra.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Enforces {@code modules} JWT claim against API path prefixes. Skips when disabled, when path is
 * exempt, or when there is no / invalid Bearer token (downstream services still validate auth).
 * Empty or missing {@code modules} claim → full module list (backward compatible).
 */
@Component
public class ModuleAccessGatewayFilter implements GlobalFilter, Ordered {

    private static final List<String> ALL_MODULES = List.of(
            "alerts", "cameras", "sirens", "water", "pumps", "zones", "devices", "workers", "analytics", "mdm"
    );

    /** Longest prefix first */
    private static final List<Map.Entry<String, String>> PATH_MODULE_PREFIXES = List.of(
            Map.entry("/api/v1/mdm", "mdm"),
            Map.entry("/api/v1/water/motors", "pumps"),
            Map.entry("/api/v1/water", "water"),
            Map.entry("/api/v1/cameras", "cameras"),
            Map.entry("/api/v1/zones", "zones"),
            Map.entry("/api/v1/nodes", "devices"),
            Map.entry("/api/v1/tags", "workers"),
            Map.entry("/api/v1/siren", "sirens"),
            Map.entry("/api/v1/alerts", "alerts")
    );

    private final SecretKey signingKey;
    private final boolean enabled;

    public ModuleAccessGatewayFilter(
            @Value("${jwt.secret}") String secret,
            @Value("${sc.gateway.module-filter.enabled:true}") boolean enabled) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.enabled = enabled;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        if (shouldSkipPath(path)) {
            return chain.filter(exchange);
        }
        String required = requiredModule(path);
        if (required == null) {
            return chain.filter(exchange);
        }

        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }
        String token = auth.substring(7);
        List<String> modules;
        try {
            modules = extractModules(token);
        } catch (JwtException | IllegalArgumentException e) {
            return chain.filter(exchange);
        }
        if (modules.isEmpty()) {
            modules = ALL_MODULES;
        }
        if (!modules.contains(required)) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }

    private static boolean shouldSkipPath(String path) {
        return path.startsWith("/actuator")
                || path.startsWith("/api/v1/auth")
                || path.startsWith("/api/v1/me")
                || path.startsWith("/api/v1/users")
                || path.startsWith("/api/v1/farms")
                || path.startsWith("/api/v1/audit");
    }

    private static String requiredModule(String path) {
        for (Map.Entry<String, String> e : PATH_MODULE_PREFIXES) {
            if (path.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private List<String> extractModules(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Object m = claims.get("modules");
        if (m instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(o.toString());
                }
            }
            return out;
        }
        return List.of();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
