package com.sudarshanchakra.alert.controller;

import com.sudarshanchakra.alert.audit.Auditable;
import com.sudarshanchakra.alert.dto.AlertPayload;
import com.sudarshanchakra.alert.dto.AlertResponse;
import com.sudarshanchakra.alert.dto.AlertUpdateRequest;
import com.sudarshanchakra.alert.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Alert management endpoints")
public class AlertController {

    private final AlertService alertService;

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_alerts:acknowledge')")
    @Operation(summary = "Create alert", description = "Ingest an alert (edge, simulator, or integration). Same JSON shape as MQTT alert payload.")
    public ResponseEntity<AlertResponse> createAlert(@RequestBody AlertPayload payload) {
        AlertResponse created = alertService.createFromPayload(payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_alerts:view')")
    @Operation(summary = "Get all alerts", description = "Retrieve paginated and filtered alerts")
    public ResponseEntity<Page<AlertResponse>> getAlerts(
            @Parameter(description = "Filter by priority") @RequestParam(required = false) String priority,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by node ID") @RequestParam(required = false) String nodeId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(alertService.getAlerts(priority, status, nodeId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_alerts:view')")
    @Operation(summary = "Get alert by ID")
    public ResponseEntity<AlertResponse> getAlert(@PathVariable UUID id) {
        return ResponseEntity.ok(alertService.getById(id));
    }

    @PatchMapping("/{id}/acknowledge")
    @Auditable(action = "alert.acknowledge", entityType = "alert", entityId = "#id.toString()")
    @PreAuthorize("hasAuthority('PERMISSION_alerts:acknowledge')")
    @Operation(summary = "Acknowledge an alert")
    public ResponseEntity<AlertResponse> acknowledgeAlert(
            @PathVariable UUID id,
            @RequestBody(required = false) AlertUpdateRequest request) {
        return ResponseEntity.ok(alertService.acknowledge(id, request));
    }

    @PatchMapping("/{id}/resolve")
    @Auditable(action = "alert.resolve", entityType = "alert", entityId = "#id.toString()")
    @PreAuthorize("hasAuthority('PERMISSION_alerts:resolve')")
    @Operation(summary = "Resolve an alert")
    public ResponseEntity<AlertResponse> resolveAlert(
            @PathVariable UUID id,
            @RequestBody(required = false) AlertUpdateRequest request) {
        return ResponseEntity.ok(alertService.resolve(id, request));
    }

    @PatchMapping("/{id}/false-positive")
    @Auditable(action = "alert.false_positive", entityType = "alert", entityId = "#id.toString()")
    @PreAuthorize("hasAuthority('PERMISSION_alerts:resolve')")
    @Operation(summary = "Mark alert as false positive")
    public ResponseEntity<AlertResponse> markFalsePositive(
            @PathVariable UUID id,
            @RequestBody(required = false) AlertUpdateRequest request) {
        return ResponseEntity.ok(alertService.markFalsePositive(id, request));
    }
}
