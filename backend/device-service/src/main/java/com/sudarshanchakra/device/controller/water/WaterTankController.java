package com.sudarshanchakra.device.controller.water;

import com.sudarshanchakra.device.dto.water.WaterTankResponse;
import com.sudarshanchakra.device.model.water.WaterLevelReading;
import com.sudarshanchakra.device.service.water.WaterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/water/tanks")
@RequiredArgsConstructor
@Tag(name = "Water Tanks", description = "Water tank monitoring")
public class WaterTankController {

    private final WaterService waterService;

    @GetMapping
    @Operation(summary = "List all water tanks with latest readings")
    public ResponseEntity<List<WaterTankResponse>> getAllTanks() {
        return ResponseEntity.ok(waterService.getAllTanks());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single tank with current level")
    public ResponseEntity<WaterTankResponse> getTank(@PathVariable String id) {
        return ResponseEntity.ok(waterService.getTank(id));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get level readings for the last N hours (default 24)")
    public ResponseEntity<List<WaterLevelReading>> getHistory(
            @PathVariable String id,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(waterService.getHistory(id, hours));
    }
}
