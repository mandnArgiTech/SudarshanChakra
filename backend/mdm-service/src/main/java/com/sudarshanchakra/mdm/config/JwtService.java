package com.sudarshanchakra.mdm.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Validates JWTs issued by auth-service and extracts claims (no token issuance).
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;

    public JwtService(@Value("${jwt.secret}") String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public Optional<UUID> extractFarmId(String token) {
        try {
            Object v = parseClaims(token).get("farm_id");
            if (v == null || v.toString().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(v.toString()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Role claim as stored by auth-service (e.g. SUPER_ADMIN).
     */
    public Optional<String> extractRole(String token) {
        try {
            Object r = parseClaims(token).get("role");
            if (r == null || r.toString().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(r.toString());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public List<String> extractModules(String token) {
        try {
            Object m = parseClaims(token).get("modules");
            if (m instanceof List<?> list) {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    if (o != null) {
                        out.add(o.toString());
                    }
                }
                return out;
            }
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Could not extract modules: {}", e.getMessage());
        }
        return List.of();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
