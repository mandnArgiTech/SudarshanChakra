package com.sudarshanchakra.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Validates HS256 JWTs from auth-service and extracts claims for resource servers.
 */
@Slf4j
public class JwtTokenParser {

    private final SecretKey signingKey;

    public JwtTokenParser(String secret) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public ParsedJwt parse(String token) {
        Claims c = parseClaims(token);
        String username = c.getSubject();
        String role = c.get("role", String.class);
        if (role == null || role.isBlank()) {
            role = "viewer";
        }
        UUID farmId = null;
        Object farm = c.get("farm_id");
        if (farm != null && !farm.toString().isBlank()) {
            farmId = UUID.fromString(farm.toString());
        }
        List<String> permissions = extractStringList(c.get("permissions"));
        UUID userId = null;
        Object uid = c.get("user_id");
        if (uid != null && !uid.toString().isBlank()) {
            try {
                userId = UUID.fromString(uid.toString());
            } catch (IllegalArgumentException e) {
                log.debug("Invalid user_id claim: {}", uid);
            }
        }
        return new ParsedJwt(username, farmId, role.trim(), permissions, userId);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractStringList(Object raw) {
        if (raw instanceof List<?> list) {
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

    public record ParsedJwt(String username, UUID farmId, String role, List<String> permissions, UUID userId) {
        public boolean isSuperAdmin() {
            return "super_admin".equalsIgnoreCase(role);
        }
    }
}
