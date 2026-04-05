package com.sudarshanchakra.gateway.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleAccessGatewayFilterTest {

    private static final String SECRET = "module-access-gateway-test-secret-32b!!";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(pad(SECRET));

    private static byte[] pad(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        if (b.length >= 32) {
            return b;
        }
        byte[] p = new byte[32];
        System.arraycopy(b, 0, p, 0, b.length);
        return p;
    }

    private static String jwt(Map<String, Object> claims) {
        var b = Jwts.builder();
        claims.forEach(b::claim);
        return b.signWith(KEY).compact();
    }

    private final ModuleAccessGatewayFilter filter = new ModuleAccessGatewayFilter(SECRET, true);

    @Test
    void camerasPath_forbiddenWhenModulesExcludeCameras() {
        String token = jwt(Map.of("sub", "u1", "modules", List.of("alerts")));
        MockServerHttpRequest req = MockServerHttpRequest.get("http://localhost/api/v1/cameras")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange ex = MockServerWebExchange.from(req);
        StepVerifier.create(filter.filter(ex, exchange -> Mono.empty()))
                .verifyComplete();
        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void camerasPath_okWhenModulesIncludesCameras() {
        String token = jwt(Map.of("sub", "u1", "modules", List.of("cameras")));
        MockServerHttpRequest req = MockServerHttpRequest.get("http://localhost/api/v1/cameras")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange ex = MockServerWebExchange.from(req);
        StepVerifier.create(filter.filter(ex, exchange -> Mono.empty()))
                .verifyComplete();
        assertThat(ex.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void noModulesInJwt_treatedAsFullAccess() {
        String token = jwt(Map.of("sub", "u1"));
        MockServerHttpRequest req = MockServerHttpRequest.get("http://localhost/api/v1/cameras")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange ex = MockServerWebExchange.from(req);
        StepVerifier.create(filter.filter(ex, exchange -> Mono.empty()))
                .verifyComplete();
        assertThat(ex.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void publicEndpoint_noModuleCheck() {
        String token = jwt(Map.of("sub", "u1", "modules", List.of("alerts")));
        MockServerHttpRequest req = MockServerHttpRequest.post("http://localhost/api/v1/auth/login")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange ex = MockServerWebExchange.from(req);
        StepVerifier.create(filter.filter(ex, exchange -> Mono.empty()))
                .verifyComplete();
        assertThat(ex.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
    }
}
