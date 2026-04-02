# Story 04: Command Dispatch API (MQTT)

## Prerequisites
- Story 02 complete (mdm-service + RabbitMQConfig)

## Goal
Build REST endpoints to issue remote commands to devices. Commands are stored in `mdm_commands` and published via RabbitMQ to MQTT topic `farm/mdm/{deviceId}/command`. The Android device subscribes to this topic.

## Files to CREATE

### 1. `backend/mdm-service/src/main/java/com/sudarshanchakra/mdm/dto/CommandRequest.java`
```java
package com.sudarshanchakra.mdm.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CommandRequest(
    @NotNull UUID deviceId,
    @NotBlank String command,   // UPDATE_APP, LOCK_SCREEN, WIPE_DEVICE, SYNC_TELEMETRY, SET_POLICY
    JsonNode payload            // e.g. {"apk_url":"...", "version":"2.2.0"} for UPDATE_APP
) {}
```

### 2. `backend/mdm-service/src/main/java/com/sudarshanchakra/mdm/controller/CommandController.java`
```java
package com.sudarshanchakra.mdm.controller;

import com.sudarshanchakra.mdm.dto.CommandRequest;
import com.sudarshanchakra.mdm.model.MdmCommand;
import com.sudarshanchakra.mdm.service.CommandDispatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mdm/commands")
public class CommandController {

    private final CommandDispatchService commandService;

    public CommandController(CommandDispatchService commandService) {
        this.commandService = commandService;
    }

    @PostMapping
    public ResponseEntity<MdmCommand> issueCommand(@Valid @RequestBody CommandRequest request) {
        // TenantContext.getFarmId() is set by JwtAuthFilter
        MdmCommand cmd = commandService.dispatch(request);
        return ResponseEntity.ok(cmd);
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<List<MdmCommand>> getCommandHistory(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(commandService.getHistory(deviceId));
    }
}
```

### 3. `backend/mdm-service/src/main/java/com/sudarshanchakra/mdm/service/CommandDispatchService.java`
```java
package com.sudarshanchakra.mdm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.mdm.config.RabbitMQConfig;
import com.sudarshanchakra.mdm.dto.CommandRequest;
import com.sudarshanchakra.mdm.model.MdmCommand;
import com.sudarshanchakra.mdm.model.MdmDevice;
import com.sudarshanchakra.mdm.repository.MdmCommandRepository;
import com.sudarshanchakra.mdm.repository.MdmDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class CommandDispatchService {
    private static final Logger log = LoggerFactory.getLogger(CommandDispatchService.class);
    private static final Set<String> VALID_COMMANDS = Set.of(
        "UPDATE_APP", "LOCK_SCREEN", "WIPE_DEVICE", "SYNC_TELEMETRY", "SET_POLICY"
    );

    private final MdmCommandRepository commandRepo;
    private final MdmDeviceRepository deviceRepo;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public CommandDispatchService(MdmCommandRepository commandRepo,
                                   MdmDeviceRepository deviceRepo,
                                   RabbitTemplate rabbitTemplate,
                                   ObjectMapper objectMapper) {
        this.commandRepo = commandRepo;
        this.deviceRepo = deviceRepo;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public MdmCommand dispatch(CommandRequest request) {
        if (!VALID_COMMANDS.contains(request.command())) {
            throw new IllegalArgumentException("Invalid command: " + request.command());
        }

        MdmDevice device = deviceRepo.findById(request.deviceId())
            .orElseThrow(() -> new IllegalArgumentException("Device not found: " + request.deviceId()));

        MdmCommand cmd = new MdmCommand();
        cmd.setDeviceId(device.getId());
        cmd.setFarmId(device.getFarmId());
        cmd.setCommand(request.command());
        cmd.setPayload(request.payload() != null ? request.payload().toString() : null);
        cmd.setStatus("pending");
        cmd.setIssuedAt(Instant.now());
        cmd = commandRepo.save(cmd);

        // Publish to MQTT via RabbitMQ
        // MQTT topic: farm/mdm/{deviceId}/command
        // RabbitMQ routing key: farm.mdm.{deviceId}.command
        String routingKey = "farm.mdm." + device.getId() + ".command";
        try {
            Map<String, Object> mqttPayload = Map.of(
                "command_id", cmd.getId().toString(),
                "command", cmd.getCommand(),
                "payload", request.payload() != null ? request.payload() : Map.of(),
                "issued_at", cmd.getIssuedAt().toString()
            );
            String json = objectMapper.writeValueAsString(mqttPayload);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_MDM_COMMANDS, routingKey, json);
            log.info("Command {} dispatched to device {} via MQTT", cmd.getCommand(), device.getId());
        } catch (Exception e) {
            log.error("Failed to dispatch MQTT command: {}", e.getMessage());
            cmd.setStatus("failed");
            cmd.setResult("{\"error\":\"" + e.getMessage() + "\"}");
            commandRepo.save(cmd);
        }

        return cmd;
    }

    public List<MdmCommand> getHistory(UUID deviceId) {
        return commandRepo.findByDeviceIdOrderByIssuedAtDesc(deviceId);
    }

    @Transactional
    public void acknowledgeCommand(UUID commandId, boolean success, String resultJson) {
        commandRepo.findById(commandId).ifPresent(cmd -> {
            cmd.setStatus(success ? "executed" : "failed");
            cmd.setExecutedAt(Instant.now());
            cmd.setResult(resultJson);
            commandRepo.save(cmd);
        });
    }
}
```

### 4. `backend/mdm-service/src/main/java/com/sudarshanchakra/mdm/controller/DeviceController.java`
```java
package com.sudarshanchakra.mdm.controller;

import com.sudarshanchakra.mdm.model.MdmDevice;
import com.sudarshanchakra.mdm.service.DeviceManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mdm/devices")
public class DeviceController {

    private final DeviceManagementService deviceService;

    public DeviceController(DeviceManagementService deviceService) {
        this.deviceService = deviceService;
    }

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
    public ResponseEntity<?> getUsage(@PathVariable UUID id,
                                       @RequestParam String from,
                                       @RequestParam String to) {
        return ResponseEntity.ok(deviceService.getUsage(id, from, to));
    }

    @GetMapping("/{id}/calls")
    public ResponseEntity<?> getCalls(@PathVariable UUID id,
                                       @RequestParam String from,
                                       @RequestParam String to) {
        return ResponseEntity.ok(deviceService.getCalls(id, from, to));
    }

    @GetMapping("/{id}/screentime")
    public ResponseEntity<?> getScreenTime(@PathVariable UUID id,
                                            @RequestParam String from,
                                            @RequestParam String to) {
        return ResponseEntity.ok(deviceService.getScreenTime(id, from, to));
    }
}
```

### 5. `backend/mdm-service/src/main/java/com/sudarshanchakra/mdm/service/DeviceManagementService.java`
Implement the service layer:
- `listByFarm()`: uses `TenantContext.getFarmId()` to filter
- `register()`: sets farmId from TenantContext, validates androidId uniqueness
- `update()`: validates ownership (farm_id match)
- `decommission()`: sets status="decommissioned"
- `getUsage()`: delegates to AppUsageRepository with date range
- `getCalls()`: delegates to CallLogRepository with date range
- `getScreenTime()`: delegates to ScreenTimeRepository with date range

Follow the exact pattern of `backend/auth-service/.../service/FarmService.java`.

## Files to MODIFY
None (all new files).

## Verification
```bash
# Issue a command
curl -X POST http://localhost:8085/api/v1/mdm/commands \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"deviceId":"<device-uuid>","command":"SYNC_TELEMETRY","payload":{}}'
# Expected: JSON with command_id, status="pending"

# Check command history
curl http://localhost:8085/api/v1/mdm/commands/<device-uuid> \
  -H "Authorization: Bearer $TOKEN"
# Expected: array of commands
```

---

# Story 05: OTA Package Management API

## Prerequisites
- Story 02 complete

## Goal
CRUD endpoints for managing OTA APK packages. Admin uploads version info + APK URL, devices download and silent-install.

## Files to CREATE

### 1. `backend/mdm-service/src/main/java/com/sudarshanchakra/mdm/controller/OtaController.java`
Standard CRUD controller for `OtaPackage` entity:
- `POST /api/v1/mdm/ota/packages` — create new version (farmId from TenantContext)
- `GET /api/v1/mdm/ota/packages` — list versions for farm
- `GET /api/v1/mdm/ota/packages/latest` — latest version
- `DELETE /api/v1/mdm/ota/packages/{id}` — remove version

### 2. `backend/mdm-service/src/main/java/com/sudarshanchakra/mdm/service/OtaService.java`
- `create()`: validate version string, store APK URL + sha256
- `getLatest()`: return newest by created_at for farm
- `delete()`: remove package record

### 3. `backend/mdm-service/src/main/java/com/sudarshanchakra/mdm/dto/OtaPackageRequest.java`
```java
public record OtaPackageRequest(
    String version,
    String apkUrl,
    String apkSha256,
    Long apkSizeBytes,
    String releaseNotes,
    boolean mandatory
) {}
```

Follow exact patterns from DeviceController/DeviceManagementService.

## Verification
```bash
curl -X POST http://localhost:8085/api/v1/mdm/ota/packages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"version":"2.2.0","apkUrl":"https://releases.example.com/sc-2.2.0.apk","apkSha256":"abc123","apkSizeBytes":15000000,"releaseNotes":"Bug fixes","mandatory":false}'
```
