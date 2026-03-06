package com.sudarshanchakra.auth.service;

import com.sudarshanchakra.auth.dto.*;
import com.sudarshanchakra.auth.model.Role;
import com.sudarshanchakra.auth.model.User;
import com.sudarshanchakra.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalArgumentException("Account is deactivated");
        }

        user.setLastLogin(OffsetDateTime.now());
        userRepository.save(user);

        String token = jwtService.generateToken(user.getUsername(), user.getRole().getValue());
        String refreshToken = jwtService.generateRefreshToken(user.getUsername());

        log.info("User '{}' logged in successfully", user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .user(toUserResponse(user))
                .build();
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }

        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        Role role = request.getRole() != null ? Role.fromValue(request.getRole()) : Role.VIEWER;
        UUID farmId = request.getFarmId() != null
                ? UUID.fromString(request.getFarmId())
                : UUID.fromString("a0000000-0000-0000-0000-000000000001");

        User user = User.builder()
                .farmId(farmId)
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .active(true)
                .build();

        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getUsername(), user.getRole().getValue());
        String refreshToken = jwtService.generateRefreshToken(user.getUsername());

        log.info("User '{}' registered successfully with role '{}'", user.getUsername(), role.getValue());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .user(toUserResponse(user))
                .build();
    }

    private UserResponse toUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().getValue())
                .build();
    }
}
