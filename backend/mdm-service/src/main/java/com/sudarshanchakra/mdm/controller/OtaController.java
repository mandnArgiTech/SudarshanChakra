package com.sudarshanchakra.mdm.controller;

import com.sudarshanchakra.mdm.dto.OtaPackageRequest;
import com.sudarshanchakra.mdm.model.OtaPackage;
import com.sudarshanchakra.mdm.service.OtaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mdm/ota/packages")
@RequiredArgsConstructor
public class OtaController {

    private final OtaService otaService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER')")
    public ResponseEntity<OtaPackage> create(@Valid @RequestBody OtaPackageRequest request) {
        return ResponseEntity.ok(otaService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<OtaPackage>> list() {
        return ResponseEntity.ok(otaService.listByFarm());
    }

    @GetMapping("/latest")
    public ResponseEntity<OtaPackage> latest() {
        return ResponseEntity.ok(otaService.getLatest());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        otaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
