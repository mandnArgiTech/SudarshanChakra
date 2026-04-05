package com.sudarshanchakra.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenParserTest {

    private static byte[] pad(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        if (b.length >= 32) {
            return b;
        }
        byte[] p = new byte[32];
        System.arraycopy(b, 0, p, 0, b.length);
        return p;
    }

    @Test
    void parsesRoleFarmPermissionsAndSuperAdmin() {
        String secret = "unit-test-jwt-parser-secret-32bytes!!";
        SecretKey key = Keys.hmacShaKeyFor(pad(secret));
        UUID farm = UUID.randomUUID();
        String token = Jwts.builder()
                .subject("alice")
                .claim("role", "admin")
                .claim("farm_id", farm.toString())
                .claim("permissions", List.of("devices:view", "alerts:acknowledge"))
                .signWith(key)
                .compact();

        JwtTokenParser parser = new JwtTokenParser(secret);
        assertThat(parser.validate(token)).isTrue();
        JwtTokenParser.ParsedJwt pj = parser.parse(token);
        assertThat(pj.username()).isEqualTo("alice");
        assertThat(pj.role()).isEqualToIgnoringCase("admin");
        assertThat(pj.farmId()).isEqualTo(farm);
        assertThat(pj.permissions()).containsExactly("devices:view", "alerts:acknowledge");
        assertThat(pj.isSuperAdmin()).isFalse();
        assertThat(pj.userId()).isNull();
    }

    @Test
    void parsesUserIdClaim() {
        String secret = "unit-test-jwt-parser-secret-32bytes!!";
        SecretKey key = Keys.hmacShaKeyFor(pad(secret));
        UUID farm = UUID.randomUUID();
        UUID uid = UUID.randomUUID();
        String token = Jwts.builder()
                .subject("bob")
                .claim("role", "viewer")
                .claim("farm_id", farm.toString())
                .claim("user_id", uid.toString())
                .claim("permissions", List.of())
                .signWith(key)
                .compact();
        JwtTokenParser.ParsedJwt pj = new JwtTokenParser(secret).parse(token);
        assertThat(pj.userId()).isEqualTo(uid);
    }

    @Test
    void superAdminFlag() {
        String secret = "unit-test-jwt-parser-secret-32bytes!!";
        SecretKey key = Keys.hmacShaKeyFor(pad(secret));
        String token = Jwts.builder()
                .subject("root")
                .claim("role", "super_admin")
                .claim("permissions", List.of("farms:manage"))
                .signWith(key)
                .compact();
        JwtTokenParser.ParsedJwt pj = new JwtTokenParser(secret).parse(token);
        assertThat(pj.isSuperAdmin()).isTrue();
    }
}
