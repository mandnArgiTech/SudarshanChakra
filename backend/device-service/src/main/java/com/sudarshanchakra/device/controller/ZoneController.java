package com.sudarshanchakra.device.controller;

import com.sudarshanchakra.device.model.Zone;
import com.sudarshanchakra.device.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/zones")
@RequiredArgsConstructor
@Tag(name = "Zones", description = "Virtual fence zone management")
public class ZoneController {

    private final DeviceService deviceService;

    @GetMapping
    @Operation(summary = "List all zones")
    public ResponseEntity<List<Zone>> getAllZones() {
        return ResponseEntity.ok(deviceService.getAllZones());
    }

    @GetMapping(params = "cameraId")
    @Operation(summary = "List zones by camera ID")
    public ResponseEntity<List<Zone>> getZonesByCameraId(@RequestParam String cameraId) {
        return ResponseEntity.ok(deviceService.getZonesByCameraId(cameraId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get zone by ID")
    public ResponseEntity<Zone> getZoneById(@PathVariable String id) {
        return ResponseEntity.ok(deviceService.getZoneById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new zone")
    public ResponseEntity<Zone> createZone(@Valid @RequestBody Zone zone) {
        return ResponseEntity.ok(deviceService.createZone(zone));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a zone")
    public ResponseEntity<Void> deleteZone(@PathVariable String id) {
        deviceService.deleteZone(id);
        return ResponseEntity.noContent().build();
    }
}
