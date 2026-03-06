package com.sudarshanchakra.device.controller;

import com.sudarshanchakra.device.model.WorkerTag;
import com.sudarshanchakra.device.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Tag(name = "Worker Tags", description = "Worker tag (ESP32/LoRa) management")
public class WorkerTagController {

    private final DeviceService deviceService;

    @GetMapping
    @Operation(summary = "List all worker tags")
    public ResponseEntity<List<WorkerTag>> getAllTags() {
        return ResponseEntity.ok(deviceService.getAllTags());
    }

    @GetMapping(params = "farmId")
    @Operation(summary = "List worker tags by farm ID")
    public ResponseEntity<List<WorkerTag>> getTagsByFarmId(@RequestParam UUID farmId) {
        return ResponseEntity.ok(deviceService.getTagsByFarmId(farmId));
    }

    @GetMapping("/{tagId}")
    @Operation(summary = "Get worker tag by ID")
    public ResponseEntity<WorkerTag> getTagById(@PathVariable String tagId) {
        return ResponseEntity.ok(deviceService.getTagById(tagId));
    }

    @PostMapping
    @Operation(summary = "Register a new worker tag")
    public ResponseEntity<WorkerTag> createTag(@Valid @RequestBody WorkerTag tag) {
        return ResponseEntity.ok(deviceService.createTag(tag));
    }
}
