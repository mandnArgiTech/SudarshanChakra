package com.sudarshanchakra.device.controller;

import com.sudarshanchakra.device.model.Camera;
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
@RequestMapping("/api/v1/cameras")
@RequiredArgsConstructor
@Tag(name = "Cameras", description = "Camera management")
public class CameraController {

    private final DeviceService deviceService;

    @GetMapping
    @Operation(summary = "List all cameras")
    public ResponseEntity<List<Camera>> getAllCameras() {
        return ResponseEntity.ok(deviceService.getAllCameras());
    }

    @GetMapping(params = "nodeId")
    @Operation(summary = "List cameras by node ID")
    public ResponseEntity<List<Camera>> getCamerasByNodeId(@RequestParam String nodeId) {
        return ResponseEntity.ok(deviceService.getCamerasByNodeId(nodeId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get camera by ID")
    public ResponseEntity<Camera> getCameraById(@PathVariable String id) {
        return ResponseEntity.ok(deviceService.getCameraById(id));
    }

    @PostMapping
    @Operation(summary = "Register a new camera")
    public ResponseEntity<Camera> createCamera(@Valid @RequestBody Camera camera) {
        return ResponseEntity.ok(deviceService.createCamera(camera));
    }
}
