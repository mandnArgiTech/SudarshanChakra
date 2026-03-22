package com.sudarshanchakra.auth.controller;

import com.sudarshanchakra.auth.dto.FarmRequest;
import com.sudarshanchakra.auth.dto.FarmResponse;
import com.sudarshanchakra.auth.service.FarmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/farms")
@RequiredArgsConstructor
@Tag(name = "Farms", description = "Tenant farm management (super admin)")
@SecurityRequirement(name = "bearerAuth")
public class FarmController {

    private final FarmService farmService;

    @GetMapping
    @Operation(summary = "List all farms")
    public ResponseEntity<List<FarmResponse>> list() {
        return ResponseEntity.ok(farmService.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get farm by id")
    public ResponseEntity<FarmResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(farmService.get(id));
    }

    @PostMapping
    @Operation(summary = "Create farm (optional initial admin user)")
    public ResponseEntity<FarmResponse> create(
            @Validated(FarmRequest.Create.class) @RequestBody FarmRequest request) {
        return ResponseEntity.ok(farmService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update farm")
    public ResponseEntity<FarmResponse> update(@PathVariable UUID id, @Valid @RequestBody FarmRequest request) {
        return ResponseEntity.ok(farmService.update(id, request));
    }

    @PatchMapping("/{id}/suspend")
    @Operation(summary = "Suspend farm")
    public ResponseEntity<FarmResponse> suspend(@PathVariable UUID id) {
        return ResponseEntity.ok(farmService.suspend(id));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate farm")
    public ResponseEntity<FarmResponse> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(farmService.activate(id));
    }
}
