# Story 03: Telemetry Batch Ingestion API

## Prerequisites
- Story 02 complete (mdm-service running, entities + repos exist)

## Goal
Build the REST endpoint that Android devices POST telemetry batches to. Handles app usage, call logs, and screen time in one request. Uses UPSERT for idempotency.

## Reference
- `docs/mdm/MDM_KIOSK_PLAN.md` — "Telemetry Batch Upload Payload" section for exact JSON structure

## Files to CREATE

### 1. `backend/mdm-service/src/main/java/com/sudarshanchakra/mdm/dto/TelemetryBatchRequest.java`
```java
package com.sudarshanchakra.mdm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record TelemetryBatchRequest(
    @NotNull String deviceId,
    @NotBlank String androidId,
    String appVersion,
    @Valid List<AppUsageDto> appUsage,
    @Valid List<CallLogDto> callLogs,
    ScreenTimeDto screenTime
) {
    public record AppUsageDto(
        @NotBlank String date,
        @NotBlank String packageName,
        String appLabel,
        int foregroundTimeSec,
        int launchCount,
        String category
    ) {}

    public record CallLogDto(
        String phoneNumberMasked,
        @NotBlank String callType,
        @NotBlank String callTimestamp,
        int durationSec,
        String contactName
    ) {}

    public record ScreenTimeDto(
        @NotBlank String date,
        int totalScreenTimeSec,
        int unlockCount
    ) {}
}
```

### 2. `backend/mdm-service/src/main/java/com/sudarshanchakra/mdm/controller/TelemetryController.java`
```java
package com.sudarshanchakra.mdm.controller;

import com.sudarshanchakra.mdm.dto.TelemetryBatchRequest;
import com.sudarshanchakra.mdm.service.TelemetryIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mdm/telemetry")
public class TelemetryController {

    private final TelemetryIngestionService ingestionService;

    public TelemetryController(TelemetryIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> uploadBatch(
            @Valid @RequestBody TelemetryBatchRequest request) {
        var result = ingestionService.processBatch(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Map<String, String>> heartbeat(
            @RequestBody Map<String, String> body) {
        ingestionService.recordHeartbeat(body.get("device_id"), body.get("android_id"));
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
```

### 3. `backend/mdm-service/src/main/java/com/sudarshanchakra/mdm/service/TelemetryIngestionService.java`
```java
package com.sudarshanchakra.mdm.service;

import com.sudarshanchakra.mdm.dto.TelemetryBatchRequest;
import com.sudarshanchakra.mdm.model.*;
import com.sudarshanchakra.mdm.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class TelemetryIngestionService {
    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestionService.class);

    private final MdmDeviceRepository deviceRepo;
    private final AppUsageRepository usageRepo;
    private final CallLogRepository callRepo;
    private final ScreenTimeRepository screenRepo;

    public TelemetryIngestionService(MdmDeviceRepository deviceRepo,
                                      AppUsageRepository usageRepo,
                                      CallLogRepository callRepo,
                                      ScreenTimeRepository screenRepo) {
        this.deviceRepo = deviceRepo;
        this.usageRepo = usageRepo;
        this.callRepo = callRepo;
        this.screenRepo = screenRepo;
    }

    @Transactional
    public Map<String, Object> processBatch(TelemetryBatchRequest req) {
        MdmDevice device = deviceRepo.findByAndroidId(req.androidId());
        if (device == null) {
            throw new IllegalArgumentException("Unknown device: " + req.androidId());
        }
        UUID deviceId = device.getId();
        UUID farmId = device.getFarmId();

        int usageCount = 0, callCount = 0, screenCount = 0;

        // App usage — UPSERT by (device_id, date, package_name)
        if (req.appUsage() != null) {
            for (var u : req.appUsage()) {
                LocalDate date = LocalDate.parse(u.date());
                AppUsage existing = usageRepo.findByDeviceIdAndDateAndPackageName(deviceId, date, u.packageName());
                if (existing != null) {
                    existing.setForegroundTimeSec(u.foregroundTimeSec());
                    existing.setLaunchCount(u.launchCount());
                    usageRepo.save(existing);
                } else {
                    AppUsage entity = new AppUsage();
                    entity.setDeviceId(deviceId);
                    entity.setFarmId(farmId);
                    entity.setDate(date);
                    entity.setPackageName(u.packageName());
                    entity.setAppLabel(u.appLabel());
                    entity.setForegroundTimeSec(u.foregroundTimeSec());
                    entity.setLaunchCount(u.launchCount());
                    entity.setCategory(u.category());
                    usageRepo.save(entity);
                }
                usageCount++;
            }
        }

        // Call logs — INSERT (no dedup, each call is unique by timestamp)
        if (req.callLogs() != null) {
            for (var c : req.callLogs()) {
                CallLogEntry entry = new CallLogEntry();
                entry.setDeviceId(deviceId);
                entry.setFarmId(farmId);
                entry.setPhoneNumberMasked(c.phoneNumberMasked());
                entry.setCallType(c.callType());
                entry.setCallTimestamp(Instant.parse(c.callTimestamp()));
                entry.setDurationSec(c.durationSec());
                entry.setContactName(c.contactName());
                callRepo.save(entry);
                callCount++;
            }
        }

        // Screen time — UPSERT by (device_id, date)
        if (req.screenTime() != null) {
            LocalDate date = LocalDate.parse(req.screenTime().date());
            ScreenTime existing = screenRepo.findByDeviceIdAndDate(deviceId, date);
            if (existing != null) {
                existing.setTotalScreenTimeSec(req.screenTime().totalScreenTimeSec());
                existing.setUnlockCount(req.screenTime().unlockCount());
                screenRepo.save(existing);
            } else {
                ScreenTime st = new ScreenTime();
                st.setDeviceId(deviceId);
                st.setFarmId(farmId);
                st.setDate(date);
                st.setTotalScreenTimeSec(req.screenTime().totalScreenTimeSec());
                st.setUnlockCount(req.screenTime().unlockCount());
                screenRepo.save(st);
            }
            screenCount++;
        }

        // Update device last sync time
        device.setLastTelemetrySync(Instant.now());
        device.setAppVersion(req.appVersion());
        deviceRepo.save(device);

        log.info("Telemetry batch for device {}: {} usage, {} calls, {} screen",
                 deviceId, usageCount, callCount, screenCount);

        return Map.of(
            "status", "ok",
            "usage_records", usageCount,
            "call_records", callCount,
            "screen_records", screenCount
        );
    }

    public void recordHeartbeat(String deviceId, String androidId) {
        MdmDevice device = androidId != null
            ? deviceRepo.findByAndroidId(androidId)
            : deviceRepo.findById(UUID.fromString(deviceId)).orElse(null);
        if (device != null) {
            device.setLastHeartbeat(Instant.now());
            deviceRepo.save(device);
        }
    }
}
```

### 4. Add to `AppUsageRepository.java`
```java
AppUsage findByDeviceIdAndDateAndPackageName(UUID deviceId, LocalDate date, String packageName);
```

### 4b. Add to `ScreenTimeRepository.java`
```java
ScreenTime findByDeviceIdAndDate(UUID deviceId, LocalDate date);
```

## Files to MODIFY
### `backend/api-gateway/src/main/resources/application.yml`
Ensure `/api/v1/mdm/**` route exists (done in Story 02). No additional changes.

## Verification
```bash
# Register a test device first
curl -X POST http://localhost:8085/api/v1/mdm/devices \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"deviceName":"Test Phone","androidId":"test123","farmId":"a0000000-0000-0000-0000-000000000001"}'

# Upload telemetry batch
curl -X POST http://localhost:8085/api/v1/mdm/telemetry/batch \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "deviceId":"<uuid from above>",
    "androidId":"test123",
    "appVersion":"2.1.0",
    "appUsage":[{"date":"2026-03-22","packageName":"com.whatsapp","appLabel":"WhatsApp","foregroundTimeSec":3420,"launchCount":15,"category":"social"}],
    "callLogs":[{"phoneNumberMasked":"****5678","callType":"outgoing","callTimestamp":"2026-03-22T10:15:00Z","durationSec":180,"contactName":"Supplier"}],
    "screenTime":{"date":"2026-03-22","totalScreenTimeSec":28800,"unlockCount":45}
  }'
# Expected: {"status":"ok","usage_records":1,"call_records":1,"screen_records":1}
```

---

## ADDENDUM: Location in Telemetry Batch

### Update `TelemetryBatchRequest.java` — add location array:
```java
public record TelemetryBatchRequest(
    // ... existing fields ...
    @Valid List<LocationDto> locations     // NEW
) {
    // ... existing inner records ...

    public record LocationDto(
        double latitude,
        double longitude,
        Float accuracyMeters,
        Float altitudeMeters,
        Float speedMps,
        Float bearing,
        String provider,
        Integer batteryPercent,
        @NotBlank String recordedAt        // ISO 8601
    ) {}
}
```

### Update `TelemetryIngestionService.processBatch()` — add location ingestion:
```java
// After screen time processing, add:
int locationCount = 0;
if (req.locations() != null) {
    for (var loc : req.locations()) {
        LocationHistory lh = new LocationHistory();
        lh.setDeviceId(deviceId);
        lh.setFarmId(farmId);
        lh.setLatitude(loc.latitude());
        lh.setLongitude(loc.longitude());
        lh.setAccuracyMeters(loc.accuracyMeters());
        lh.setAltitudeMeters(loc.altitudeMeters());
        lh.setSpeedMps(loc.speedMps());
        lh.setBearing(loc.bearing());
        lh.setProvider(loc.provider());
        lh.setBatteryPercent(loc.batteryPercent());
        lh.setRecordedAt(Instant.parse(loc.recordedAt()));
        locationRepo.save(lh);
        locationCount++;
    }
    // Update device last known location
    if (!req.locations().isEmpty()) {
        var latest = req.locations().get(req.locations().size() - 1);
        device.setLastLatitude(latest.latitude());
        device.setLastLongitude(latest.longitude());
        device.setLastLocationAt(Instant.parse(latest.recordedAt()));
    }
}
```

Add `locationRepo` injection and `"location_records", locationCount` to the result map.
