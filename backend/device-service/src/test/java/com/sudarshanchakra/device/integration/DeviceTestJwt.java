package com.sudarshanchakra.device.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * HS256 tokens for device-service integration tests (must match jwt.secret in application-integration-test.yml).
 */
public final class DeviceTestJwt {

    public static final String SECRET = "integration-test-jwt-secret-32bytes-minimum!!";

    private static final SecretKey KEY = Keys.hmacShaKeyFor(padSecret(SECRET));

    private static byte[] padSecret(String s) {
        byte[] keyBytes = s.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length >= 32) {
            return keyBytes;
        }
        byte[] padded = new byte[32];
        System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
        return padded;
    }

    /** Admin-like token scoped to a farm with permissions used by integration tests. */
    public static String bearerForFarm(UUID farmId) {
        List<String> perms = List.of(
                "devices:view",
                "devices:manage",
                "cameras:view",
                "cameras:manage",
                "zones:view",
                "zones:manage",
                "water:view",
                "water:manage",
                "pumps:view",
                "pumps:control");
        String token = Jwts.builder()
                .subject("integration-user")
                .claim("role", "admin")
                .claim("farm_id", farmId.toString())
                .claim("permissions", perms)
                .signWith(KEY)
                .compact();
        return "Bearer " + token;
    }

    private DeviceTestJwt() {
    }
}
