package com.sudarshanchakra.auth.config;

import com.sudarshanchakra.auth.model.Role;
import com.sudarshanchakra.auth.model.User;
import com.sudarshanchakra.auth.repository.UserRepository;
import com.sudarshanchakra.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FilterChain filterChain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthFilter(jwtService, userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthHeader_continuesChain() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilterInternal(req, res, filterChain);
        verify(filterChain).doFilter(req, res);
        verify(jwtService, never()).validateToken(anyString());
    }

    @Test
    void invalidToken_continuesWithoutAuth() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer bad");
        MockHttpServletResponse res = new MockHttpServletResponse();
        when(jwtService.validateToken("bad")).thenReturn(false);

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void validToken_setsAuthentication() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer good.tok");
        MockHttpServletResponse res = new MockHttpServletResponse();
        when(jwtService.validateToken("good.tok")).thenReturn(true);
        when(jwtService.extractUsername("good.tok")).thenReturn("alice");
        User u = User.builder()
                .id(UUID.randomUUID())
                .farmId(UUID.randomUUID())
                .username("alice")
                .passwordHash("h")
                .role(Role.VIEWER)
                .active(true)
                .build();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(u));

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("alice");
    }

    @Test
    void validToken_inactiveUser_noAuth() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer tok");
        MockHttpServletResponse res = new MockHttpServletResponse();
        when(jwtService.validateToken("tok")).thenReturn(true);
        when(jwtService.extractUsername("tok")).thenReturn("inactive");
        User u = User.builder()
                .id(UUID.randomUUID())
                .farmId(UUID.randomUUID())
                .username("inactive")
                .passwordHash("h")
                .role(Role.VIEWER)
                .active(false)
                .build();
        when(userRepository.findByUsername("inactive")).thenReturn(Optional.of(u));

        filter.doFilterInternal(req, res, filterChain);

        verify(filterChain).doFilter(req, res);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
