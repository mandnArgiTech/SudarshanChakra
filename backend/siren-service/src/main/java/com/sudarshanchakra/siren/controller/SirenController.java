package com.sudarshanchakra.siren.controller;

import com.sudarshanchakra.siren.dto.SirenRequest;
import com.sudarshanchakra.siren.dto.SirenResponse;
import com.sudarshanchakra.siren.model.SirenAction;
import com.sudarshanchakra.siren.service.SirenCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/siren")
@RequiredArgsConstructor
@Tag(name = "Siren", description = "Siren control endpoints")
public class SirenController {

    private final SirenCommandService sirenCommandService;

    @PostMapping("/trigger")
    @PreAuthorize("hasAuthority('PERMISSION_sirens:trigger')")
    @Operation(summary = "Trigger siren", description = "Trigger siren on a target edge node")
    public ResponseEntity<SirenResponse> triggerSiren(@RequestBody SirenRequest request) {
        return ResponseEntity.ok(sirenCommandService.triggerSiren(request));
    }

    @PostMapping("/stop")
    @PreAuthorize("hasAuthority('PERMISSION_sirens:trigger')")
    @Operation(summary = "Stop siren", description = "Stop siren on a target edge node")
    public ResponseEntity<SirenResponse> stopSiren(@RequestBody SirenRequest request) {
        return ResponseEntity.ok(sirenCommandService.stopSiren(request));
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('PERMISSION_sirens:view')")
    @Operation(summary = "Get siren action history", description = "Retrieve paginated siren action audit log")
    public ResponseEntity<Page<SirenAction>> getHistory(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(sirenCommandService.getHistory(pageable));
    }
}
