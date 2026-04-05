package com.sudarshanchakra.auth.service;

import com.sudarshanchakra.auth.support.ModuleConstants;
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
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(String username, String role, UUID farmId, UUID userId,
                                List<String> modules, List<String> permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        List<String> mod = modules == null || modules.isEmpty() ? ModuleConstants.ALL_MODULES : modules;
        List<String> perm = permissions == null ? List.of() : permissions;

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .claim("farm_id", farmId != null ? farmId.toString() : null)
                .claim("user_id", userId != null ? userId.toString() : null)
                .claim("modules", mod)
                .claim("permissions", perm)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs * 7);

        return Jwts.builder()
                .subject(username)
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
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
     * Modules from JWT; empty if claim missing (callers should treat as full access for backward compatibility).
     */
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

    public List<String> extractPermissions(String token) {
        try {
            Object m = parseClaims(token).get("permissions");
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
            log.debug("Could not extract permissions: {}", e.getMessage());
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
