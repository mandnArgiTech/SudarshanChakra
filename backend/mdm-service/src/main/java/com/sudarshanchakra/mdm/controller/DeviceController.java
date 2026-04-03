package com.sudarshanchakra.mdm.controller;

import com.sudarshanchakra.mdm.model.MdmDevice;
import com.sudarshanchakra.mdm.service.DeviceManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mdm/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceManagementService deviceService;

    @GetMapping
    public ResponseEntity<List<MdmDevice>> listDevices() {
        return ResponseEntity.ok(deviceService.listByFarm());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MdmDevice> getDevice(@PathVariable UUID id) {
        return ResponseEntity.ok(deviceService.getById(id));
    }

    @PostMapping
    public ResponseEntity<MdmDevice> registerDevice(@RequestBody MdmDevice device) {
        return ResponseEntity.ok(deviceService.register(device));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MdmDevice> updateDevice(@PathVariable UUID id, @RequestBody MdmDevice device) {
        return ResponseEntity.ok(deviceService.update(id, device));
    }

    @PatchMapping("/{id}/decommission")
    public ResponseEntity<MdmDevice> decommission(@PathVariable UUID id) {
        return ResponseEntity.ok(deviceService.decommission(id));
    }

    @GetMapping("/{id}/usage")
    public ResponseEntity<?> getUsage(
            @PathVariable UUID id,
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(deviceService.getUsage(id, from, to));
    }

    @GetMapping("/{id}/calls")
    public ResponseEntity<?> getCalls(
            @PathVariable UUID id,
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(deviceService.getCalls(id, from, to));
    }

    @GetMapping("/{id}/screentime")
    public ResponseEntity<?> getScreenTime(
            @PathVariable UUID id,
            @RequestParam String from,
            @RequestParam String to) {
        return ResponseEntity.ok(deviceService.getScreenTime(id, from, to));
    }
}
