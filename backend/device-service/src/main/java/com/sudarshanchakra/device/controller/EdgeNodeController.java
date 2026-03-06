package com.sudarshanchakra.device.controller;

import com.sudarshanchakra.device.model.EdgeNode;
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
@RequestMapping("/api/v1/nodes")
@RequiredArgsConstructor
@Tag(name = "Edge Nodes", description = "Edge node management")
public class EdgeNodeController {

    private final DeviceService deviceService;

    @GetMapping
    @Operation(summary = "List all edge nodes")
    public ResponseEntity<List<EdgeNode>> getAllNodes() {
        return ResponseEntity.ok(deviceService.getAllNodes());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get edge node by ID")
    public ResponseEntity<EdgeNode> getNodeById(@PathVariable String id) {
        return ResponseEntity.ok(deviceService.getNodeById(id));
    }

    @PostMapping
    @Operation(summary = "Register a new edge node")
    public ResponseEntity<EdgeNode> createNode(@Valid @RequestBody EdgeNode node) {
        return ResponseEntity.ok(deviceService.createNode(node));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an edge node")
    public ResponseEntity<EdgeNode> updateNode(@PathVariable String id, @RequestBody EdgeNode updates) {
        return ResponseEntity.ok(deviceService.updateNode(id, updates));
    }
}
