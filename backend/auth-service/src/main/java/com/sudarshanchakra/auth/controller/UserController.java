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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER')")
    @Operation(summary = "List users", description = "Super admin: all users; Admin/Manager: same farm")
    public ResponseEntity<List<UserResponse>> listUsers() {
        return ResponseEntity.ok(userService.listForPrincipal(currentUsername()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER')")
    @Operation(summary = "Get user by id")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getByIdForPrincipal(id, currentUsername()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @Operation(summary = "Create user")
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody UserCreateRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(userService.createUser(
                currentUsername(), request, clientIp(httpRequest)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @Operation(summary = "Update user")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UserUpdateRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(userService.updateUser(
                id, currentUsername(), request, clientIp(httpRequest)));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @Operation(summary = "Deactivate user")
    public ResponseEntity<Void> deactivateUser(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        userService.deactivateUser(id, currentUsername(), clientIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/me/mqtt-client-id")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update MQTT client ID", description = "Update the current user's MQTT client ID for direct push notifications")
    public ResponseEntity<Void> updateMqttClientId(@Valid @RequestBody MqttClientIdRequest request) {
        userService.updateMqttClientId(currentUsername(), request.getMqttClientId());
        return ResponseEntity.noContent().build();
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "";
    }

    private static String clientIp(HttpServletRequest req) {
        String x = req.getHeader("X-Forwarded-For");
        if (x != null && !x.isBlank()) {
            return x.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
