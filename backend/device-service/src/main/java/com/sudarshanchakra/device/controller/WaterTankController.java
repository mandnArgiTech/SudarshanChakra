package com.sudarshanchakra.device.controller;

import com.sudarshanchakra.device.model.WaterLevelReading;
import com.sudarshanchakra.device.model.WaterTank;
import com.sudarshanchakra.device.service.WaterTankService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/water/tanks")
@RequiredArgsConstructor
@Tag(name = "Water", description = "Water tank CRUD and level history")
public class WaterTankController {

    private final WaterTankService waterTankService;

    @GetMapping
    @Operation(summary = "List water tanks")
    public ResponseEntity<List<WaterTank>> list(
            @RequestParam(required = false) UUID farmId) {
        if (farmId != null) {
            return ResponseEntity.ok(waterTankService.listByFarm(farmId));
        }
        return ResponseEntity.ok(waterTankService.listAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get tank by ID")
    public ResponseEntity<WaterTank> get(@PathVariable UUID id) {
        return ResponseEntity.ok(waterTankService.getById(id));
    }

    @PostMapping
    @Operation(summary = "Register a water tank")
    public ResponseEntity<WaterTank> create(@Valid @RequestBody WaterTank tank) {
        return ResponseEntity.ok(waterTankService.create(tank));
    }

    @GetMapping("/{id}/readings")
    @Operation(summary = "Paginated level history")
    public ResponseEntity<Page<WaterLevelReading>> history(
            @PathVariable UUID id,
            @PageableDefault(size = 100) Pageable pageable) {
        return ResponseEntity.ok(waterTankService.getHistory(id, pageable));
    }
}
