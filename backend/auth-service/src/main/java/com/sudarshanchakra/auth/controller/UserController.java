package com.sudarshanchakra.auth.controller;

import com.sudarshanchakra.auth.dto.FcmTokenRequest;
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

    @PatchMapping("/me/fcm-token")
    @Operation(summary = "Update FCM token", description = "Update the current user's FCM push notification token")
    public ResponseEntity<Void> updateFcmToken(
            Authentication authentication,
            @Valid @RequestBody FcmTokenRequest request) {
        userService.updateFcmToken(authentication.getName(), request.getFcmToken());
        return ResponseEntity.noContent().build();
    }
}
