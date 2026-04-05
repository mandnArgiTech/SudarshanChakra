package com.sudarshanchakra.auth.service;

import com.sudarshanchakra.auth.dto.*;
import com.sudarshanchakra.auth.model.Farm;
import com.sudarshanchakra.auth.model.Role;
import com.sudarshanchakra.auth.model.User;
import com.sudarshanchakra.auth.repository.FarmRepository;
import com.sudarshanchakra.auth.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final FarmRepository farmRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final ModuleResolutionService moduleResolutionService;
    private final PermissionService permissionService;
    private final AuditService auditService;

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new IllegalArgumentException("Account is deactivated");
        }

        Farm farm = farmRepository.findById(user.getFarmId()).orElse(null);
        if (farm != null && "suspended".equalsIgnoreCase(farm.getStatus()) && user.getRole() != Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("Farm is suspended");
        }

        user.setLastLogin(OffsetDateTime.now());
        userRepository.save(user);

        List<String> modules = moduleResolutionService.resolveModules(user);
        List<String> perms = permissionService.effectivePermissions(user.getRole(), user.getPermissions());
        String token = jwtService.generateToken(
                user.getUsername(),
                user.getRole().getValue(),
                user.getFarmId(),
                user.getId(),
                modules,
                perms
        );
        String refreshToken = jwtService.generateRefreshToken(user.getUsername());

        auditService.log(
                user.getFarmId(),
                user.getId(),
                "user.login",
                "user",
                user.getId().toString(),
                null,
                clientIp(httpRequest)
        );

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
        if (role == Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("Invalid role for self-registration");
        }

        UUID farmId = request.getFarmId() != null
                ? UUID.fromString(request.getFarmId())
                : UUID.fromString("a0000000-0000-0000-0000-000000000001");

        if (!farmRepository.existsById(farmId)) {
            throw new IllegalArgumentException("Unknown farm");
        }

        User user = User.builder()
                .farmId(farmId)
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .active(true)
                .build();

        user = userRepository.save(user);

        List<String> modules = moduleResolutionService.resolveModules(user);
        List<String> perms = permissionService.effectivePermissions(user.getRole(), user.getPermissions());
        String token = jwtService.generateToken(
                user.getUsername(),
                user.getRole().getValue(),
                user.getFarmId(),
                user.getId(),
                modules,
                perms
        );
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
                .farmId(user.getFarmId())
                .displayName(user.getDisplayName())
                .active(user.getActive())
                .modules(moduleResolutionService.resolveModules(user))
                .permissions(permissionService.effectivePermissions(user.getRole(), user.getPermissions()))
                .build();
    }

    private static String clientIp(HttpServletRequest req) {
        if (req == null) {
            return null;
        }
        String x = req.getHeader("X-Forwarded-For");
        if (x != null && !x.isBlank()) {
            return x.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
