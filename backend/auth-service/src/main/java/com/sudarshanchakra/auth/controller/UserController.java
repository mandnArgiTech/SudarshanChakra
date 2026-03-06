package com.sudarshanchakra.auth.controller;

import com.sudarshanchakra.auth.dto.MqttClientIdRequest;
import com.sudarshanchakra.auth.dto.UserResponse;
import com.sudarshanchakra.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "List all users", description = "Admin only — returns all registered users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.findAll());
    }

    @PatchMapping("/me/mqtt-client-id")
    @Operation(summary = "Update MQTT client ID", description = "Update the current user's MQTT client ID for direct push notifications")
    public ResponseEntity<Void> updateMqttClientId(
            Authentication authentication,
            @Valid @RequestBody MqttClientIdRequest request) {
        userService.updateMqttClientId(authentication.getName(), request.getMqttClientId());
        return ResponseEntity.noContent().build();
    }
}
