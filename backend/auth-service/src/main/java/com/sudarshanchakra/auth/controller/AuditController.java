package com.sudarshanchakra.auth.controller;

import com.sudarshanchakra.auth.dto.AuditLogResponse;
import com.sudarshanchakra.auth.model.AuditLog;
import com.sudarshanchakra.auth.model.Role;
import com.sudarshanchakra.auth.model.User;
import com.sudarshanchakra.auth.repository.UserRepository;
import com.sudarshanchakra.auth.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Audit log (farm admin / super admin)")
@SecurityRequirement(name = "bearerAuth")
public class AuditController {

    private final AuditService auditService;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Paged audit events for a farm")
    public ResponseEntity<Page<AuditLogResponse>> list(
            @RequestParam(required = false) UUID farmId,
            @RequestParam(required = false) String action,
            @PageableDefault(size = 50) Pageable pageable,
            Authentication authentication) {

        User me = userRepository.findByUsername(authentication.getName()).orElseThrow();
        UUID fid = me.getRole() == Role.SUPER_ADMIN && farmId != null ? farmId : me.getFarmId();
        Page<AuditLog> page = auditService.listForFarm(fid, action, pageable);
        return ResponseEntity.ok(page.map(this::toResponse));
    }

    private AuditLogResponse toResponse(AuditLog a) {
        return AuditLogResponse.builder()
                .id(a.getId())
                .farmId(a.getFarmId())
                .userId(a.getUserId())
                .action(a.getAction())
                .entityType(a.getEntityType())
                .entityId(a.getEntityId())
                .details(a.getDetails())
                .ipAddress(a.getIpAddress())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
