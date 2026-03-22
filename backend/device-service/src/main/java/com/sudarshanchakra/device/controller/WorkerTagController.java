package com.sudarshanchakra.device.controller;

import com.sudarshanchakra.device.model.WorkerTag;
import com.sudarshanchakra.device.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
@Tag(name = "Worker Tags", description = "Worker tag (ESP32/LoRa) management")
public class WorkerTagController {

    private final DeviceService deviceService;

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_devices:view')")
    @Operation(summary = "List worker tags for the current tenant (Hibernate tenant filter)")
    public ResponseEntity<List<WorkerTag>> getAllTags() {
        return ResponseEntity.ok(deviceService.getAllTags());
    }

    @GetMapping("/{tagId}")
    @PreAuthorize("hasAuthority('PERMISSION_devices:view')")
    @Operation(summary = "Get worker tag by ID")
    public ResponseEntity<WorkerTag> getTagById(@PathVariable String tagId) {
        return ResponseEntity.ok(deviceService.getTagById(tagId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_devices:manage')")
    @Operation(summary = "Register a new worker tag")
    public ResponseEntity<WorkerTag> createTag(@Valid @RequestBody WorkerTag tag) {
        return ResponseEntity.ok(deviceService.createTag(tag));
    }
}
