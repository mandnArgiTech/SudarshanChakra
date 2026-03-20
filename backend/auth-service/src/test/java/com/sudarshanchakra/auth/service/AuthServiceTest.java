package com.sudarshanchakra.auth.service;

import com.sudarshanchakra.auth.dto.LoginRequest;
import com.sudarshanchakra.auth.dto.RegisterRequest;
import com.sudarshanchakra.auth.model.Role;
import com.sudarshanchakra.auth.model.User;
import com.sudarshanchakra.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    JwtService jwtService;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    AuthService authService;

    User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(UUID.randomUUID())
                .farmId(UUID.randomUUID())
                .username("tester")
                .email("t@x.com")
                .passwordHash("hash")
                .role(Role.ADMIN)
                .active(true)
                .build();
    }

    @Test
    void login_success() {
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);
        when(jwtService.generateToken(any(), any())).thenReturn("tok");
        when(jwtService.generateRefreshToken(any())).thenReturn("ref");
        when(userRepository.save(any(User.class))).thenReturn(user);

        var res = authService.login(new LoginRequest("tester", "secret"));
        assertThat(res.getToken()).isEqualTo("tok");
        assertThat(res.getUser().getUsername()).isEqualTo("tester");
    }

    @Test
    void login_badPassword() {
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);
        assertThatThrownBy(() -> authService.login(new LoginRequest("tester", "wrong")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    void login_unknownUser() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody", "x")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void login_inactive() {
        user.setActive(false);
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> authService.login(new LoginRequest("tester", "secret")))
                .hasMessageContaining("deactivated");
    }

    @Test
    void register_success() {
        when(userRepository.existsByUsername("newu")).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode("p")).thenReturn("enc");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtService.generateToken(any(), any())).thenReturn("t");
        when(jwtService.generateRefreshToken(any())).thenReturn("r");

        var req = new RegisterRequest();
        req.setUsername("newu");
        req.setPassword("p");
        req.setEmail("n@e.com");
        var res = authService.register(req);
        assertThat(res.getUser().getUsername()).isEqualTo("newu");
    }

    @Test
    void register_duplicateUsername() {
        when(userRepository.existsByUsername("dup")).thenReturn(true);
        var req = new RegisterRequest();
        req.setUsername("dup");
        req.setPassword("p");
        assertThatThrownBy(() -> authService.register(req))
                .hasMessageContaining("Username already");
    }

    @Test
    void register_nullEmail_skipsEmailDuplicateCheck() {
        when(userRepository.existsByUsername("noemail")).thenReturn(false);
        when(passwordEncoder.encode("pw")).thenReturn("enc");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtService.generateToken(any(), any())).thenReturn("t");
        when(jwtService.generateRefreshToken(any())).thenReturn("r");

        var req = new RegisterRequest();
        req.setUsername("noemail");
        req.setPassword("pw");
        req.setEmail(null);
        var res = authService.register(req);
        assertThat(res.getUser().getUsername()).isEqualTo("noemail");
        verify(userRepository, never()).existsByEmail(any());
    }
}
