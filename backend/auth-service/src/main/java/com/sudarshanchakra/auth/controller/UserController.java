package com.sudarshanchakra.auth.controller;

import com.sudarshanchakra.auth.dto.MqttClientIdRequest;
import com.sudarshanchakra.auth.dto.UserCreateRequest;
import com.sudarshanchakra.auth.dto.UserResponse;
import com.sudarshanchakra.auth.dto.UserUpdateRequest;
import com.sudarshanchakra.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "List users", description = "Super admin: all users; Admin/Manager: same farm")
    public ResponseEntity<List<UserResponse>> listUsers(Authentication authentication) {
        return ResponseEntity.ok(userService.listForPrincipal(authentication.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by id")
    public ResponseEntity<UserResponse> getUser(
            @PathVariable UUID id,
            Authentication authentication) {
        return ResponseEntity.ok(userService.getByIdForPrincipal(id, authentication.getName()));
    }

    @PostMapping
    @Operation(summary = "Create user")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody UserCreateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(userService.createUser(
                authentication.getName(), request, clientIp(httpRequest)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update user")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(userService.updateUser(
                id, authentication.getName(), request, clientIp(httpRequest)));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate user")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable UUID id,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        userService.deactivateUser(id, authentication.getName(), clientIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/mqtt-client-id")
    @Operation(summary = "Update MQTT client ID", description = "Update the current user's MQTT client ID for direct push notifications")
    public ResponseEntity<Void> updateMqttClientId(
            Authentication authentication,
            @Valid @RequestBody MqttClientIdRequest request) {
        userService.updateMqttClientId(authentication.getName(), request.getMqttClientId());
        return ResponseEntity.noContent().build();
    }

    private static String clientIp(HttpServletRequest req) {
        String x = req.getHeader("X-Forwarded-For");
        if (x != null && !x.isBlank()) {
            return x.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
