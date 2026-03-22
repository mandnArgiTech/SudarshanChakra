package com.sudarshanchakra.device.controller.water;

import com.sudarshanchakra.device.dto.water.MotorCommandRequest;
import com.sudarshanchakra.device.dto.water.MotorUpdateRequest;
import com.sudarshanchakra.device.model.water.WaterMotorController;
import com.sudarshanchakra.device.service.water.WaterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/water/motors")
@RequiredArgsConstructor
@Tag(name = "Motor Controllers", description = "Pump motor control (relay and SMS)")
public class WaterMotorRestController {

    private final WaterService waterService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_pumps:view')")
    @Operation(summary = "List all motor controllers with current state")
    public ResponseEntity<List<WaterMotorController>> getAllMotors() {
        return ResponseEntity.ok(waterService.getAllMotors());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_pumps:view')")
    @Operation(summary = "Get motor controller detail")
    public ResponseEntity<WaterMotorController> getMotor(@PathVariable String id) {
        return ResponseEntity.ok(waterService.getMotor(id));
    }

    @PostMapping("/{id}/command")
    @PreAuthorize("hasAuthority('PERMISSION_pumps:control')")
    @Operation(summary = "Send pump command: pump_on | pump_off | pump_auto")
    public ResponseEntity<Map<String, String>> sendCommand(
            @PathVariable String id,
            @Valid @RequestBody MotorCommandRequest req) {
        waterService.sendMotorCommand(id, req.getCommand(), "manual_app");
        return ResponseEntity.ok(Map.of(
            "status", "published",
            "motorId", id,
            "command", req.getCommand()
        ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERMISSION_water:manage')")
    @Operation(summary = "Update motor thresholds and SMS config (Taro panel messages)")
    public ResponseEntity<WaterMotorController> updateMotor(
            @PathVariable String id,
            @RequestBody MotorUpdateRequest req) {
        return ResponseEntity.ok(waterService.updateMotor(id, req));
    }
}
