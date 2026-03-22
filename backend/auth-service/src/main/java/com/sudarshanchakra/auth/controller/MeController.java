package com.sudarshanchakra.auth.controller;

import com.sudarshanchakra.auth.model.User;
import com.sudarshanchakra.auth.repository.UserRepository;
import com.sudarshanchakra.auth.service.ModuleResolutionService;
import com.sudarshanchakra.auth.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Me", description = "Current session modules and permissions")
@SecurityRequirement(name = "bearerAuth")
public class MeController {

    private final UserRepository userRepository;
    private final ModuleResolutionService moduleResolutionService;
    private final PermissionService permissionService;

    @GetMapping("/modules")
    @Operation(summary = "Enabled modules for current user")
    public ResponseEntity<Map<String, List<String>>> modules(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(Map.of("modules", moduleResolutionService.resolveModules(user)));
    }

    @GetMapping("/permissions")
    @Operation(summary = "Effective permissions for current user")
    public ResponseEntity<Map<String, List<String>>> permissions(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "permissions",
                permissionService.effectivePermissions(user.getRole(), user.getPermissions())
        ));
    }
}
