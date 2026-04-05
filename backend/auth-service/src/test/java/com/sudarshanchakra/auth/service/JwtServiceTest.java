package com.sudarshanchakra.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm", 3600000L);
    }

    @Test
    void generateToken_containsSubjectAndRole() {
        UUID farm = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        String t = jwtService.generateToken("alice", "admin", farm, UUID.randomUUID(), List.of("alerts", "water"), List.of());
        assertThat(jwtService.extractUsername(t)).isEqualTo("alice");
        assertThat(jwtService.validateToken(t)).isTrue();
        assertThat(jwtService.extractFarmId(t)).contains(farm);
        assertThat(jwtService.extractModules(t)).containsExactly("alerts", "water");
    }

    @Test
    void generateRefreshToken_validates() {
        String r = jwtService.generateRefreshToken("bob");
        assertThat(jwtService.validateToken(r)).isTrue();
        assertThat(jwtService.extractUsername(r)).isEqualTo("bob");
    }

    @Test
    void validateToken_rejectsGarbage() {
        assertThat(jwtService.validateToken("not.a.jwt")).isFalse();
    }

    @Test
    void validateToken_rejectsEmpty() {
        assertThat(jwtService.validateToken("")).isFalse();
    }

    @Test
    void shortSecret_padded() {
        JwtService shortKey = new JwtService("short", 1000L);
        UUID farm = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        String t = shortKey.generateToken("u", "viewer", farm, null, List.of(), List.of());
        assertThat(shortKey.validateToken(t)).isTrue();
    }
}
