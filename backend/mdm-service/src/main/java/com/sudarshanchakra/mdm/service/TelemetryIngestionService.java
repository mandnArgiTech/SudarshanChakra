package com.sudarshanchakra.mdm.service;

import com.sudarshanchakra.mdm.config.TenantContext;
import com.sudarshanchakra.mdm.dto.TelemetryBatchRequest;
import com.sudarshanchakra.mdm.model.AppUsage;
import com.sudarshanchakra.mdm.model.CallLogEntry;
import com.sudarshanchakra.mdm.model.LocationHistory;
import com.sudarshanchakra.mdm.model.MdmDevice;
import com.sudarshanchakra.mdm.model.ScreenTime;
import com.sudarshanchakra.mdm.repository.AppUsageRepository;
import com.sudarshanchakra.mdm.repository.CallLogRepository;
import com.sudarshanchakra.mdm.repository.LocationHistoryRepository;
import com.sudarshanchakra.mdm.repository.MdmDeviceRepository;
import com.sudarshanchakra.mdm.repository.ScreenTimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TelemetryIngestionService {

    private final MdmDeviceRepository deviceRepo;
    private final AppUsageRepository appUsageRepository;
    private final CallLogRepository callLogRepository;
    private final ScreenTimeRepository screenTimeRepository;
    private final LocationHistoryRepository locationHistoryRepository;

    @Transactional
    public Map<String, Object> processBatch(TelemetryBatchRequest req) {
        MdmDevice device = deviceRepo.findByAndroidId(req.androidId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown device: " + req.androidId()));

        UUID callerFarm = TenantContext.getFarmId();
        if (callerFarm != null && !callerFarm.equals(device.getFarmId())) {
            throw new SecurityException("Device does not belong to caller's farm");
        }

        UUID deviceId = device.getId();
        UUID farmId = device.getFarmId();
        Instant now = Instant.now();

        int usageCount = 0;
        if (req.appUsage() != null) {
            for (TelemetryBatchRequest.AppUsageDto u : req.appUsage()) {
                LocalDate date = LocalDate.parse(u.date());
                Optional<AppUsage> existing =
                        appUsageRepository.findByDeviceIdAndDateAndPackageName(deviceId, date, u.packageName());
                if (existing.isPresent()) {
                    AppUsage au = existing.get();
                    au.setForegroundTimeSec(u.foregroundTimeSec());
                    au.setLaunchCount(u.launchCount());
                    au.setAppLabel(u.appLabel());
                    au.setCategory(u.category());
                    appUsageRepository.save(au);
                } else {
                    appUsageRepository.save(
                            AppUsage.builder()
                                    .deviceId(deviceId)
                                    .farmId(farmId)
                                    .date(date)
                                    .packageName(u.packageName())
                                    .appLabel(u.appLabel())
                                    .foregroundTimeSec(u.foregroundTimeSec())
                                    .launchCount(u.launchCount())
                                    .category(u.category())
                                    .build());
                }
                usageCount++;
            }
        }

        int callCount = 0;
        if (req.callLogs() != null) {
            for (TelemetryBatchRequest.CallLogDto c : req.callLogs()) {
                callLogRepository.save(
                        CallLogEntry.builder()
                                .deviceId(deviceId)
                                .farmId(farmId)
                                .phoneNumberMasked(c.phoneNumberMasked())
                                .callType(c.callType())
                                .callTimestamp(Instant.parse(c.callTimestamp()))
                                .durationSec(c.durationSec())
                                .contactName(c.contactName())
                                .build());
                callCount++;
            }
        }

        int screenCount = 0;
        if (req.screenTime() != null) {
            TelemetryBatchRequest.ScreenTimeDto st = req.screenTime();
            LocalDate date = LocalDate.parse(st.date());
            Optional<ScreenTime> existingSt = screenTimeRepository.findByDeviceIdAndDate(deviceId, date);
            if (existingSt.isPresent()) {
                ScreenTime s = existingSt.get();
                s.setTotalScreenTimeSec(st.totalScreenTimeSec());
                s.setUnlockCount(st.unlockCount());
                screenTimeRepository.save(s);
            } else {
                screenTimeRepository.save(
                        ScreenTime.builder()
                                .deviceId(deviceId)
                                .farmId(farmId)
                                .date(date)
                                .totalScreenTimeSec(st.totalScreenTimeSec())
                                .unlockCount(st.unlockCount())
                                .build());
            }
            screenCount = 1;
        }

        int locCount = 0;
        if (req.locations() != null) {
            for (TelemetryBatchRequest.LocationDto loc : req.locations()) {
                locationHistoryRepository.save(
                        LocationHistory.builder()
                                .deviceId(deviceId)
                                .farmId(farmId)
                                .latitude(loc.latitude())
                                .longitude(loc.longitude())
                                .accuracyMeters(loc.accuracyMeters())
                                .altitudeMeters(loc.altitudeMeters())
                                .speedMps(loc.speedMps())
                                .bearing(loc.bearing())
                                .provider(loc.provider())
                                .batteryPercent(loc.batteryPercent())
                                .recordedAt(Instant.parse(loc.recordedAt()))
                                .build());
                locCount++;
            }
        }

        device.setLastTelemetrySync(now);
        if (req.appVersion() != null && !req.appVersion().isBlank()) {
            device.setAppVersion(req.appVersion());
        }
        if (req.locations() != null && !req.locations().isEmpty()) {
            TelemetryBatchRequest.LocationDto last = req.locations().get(req.locations().size() - 1);
            device.setLastLatitude(last.latitude());
            device.setLastLongitude(last.longitude());
            device.setLastLocationAt(Instant.parse(last.recordedAt()));
        }
        deviceRepo.save(device);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ok");
        result.put("usage_records", usageCount);
        result.put("call_records", callCount);
        result.put("screen_records", screenCount);
        result.put("location_records", locCount);
        return result;
    }

    public void recordHeartbeat(Map<String, String> body) {
        String androidId = body.get("android_id");
        String deviceIdStr = body.get("device_id");
        Optional<MdmDevice> deviceOpt = Optional.empty();
        if (androidId != null && !androidId.isBlank()) {
            deviceOpt = deviceRepo.findByAndroidId(androidId);
        } else if (deviceIdStr != null && !deviceIdStr.isBlank()) {
            deviceOpt = deviceRepo.findById(UUID.fromString(deviceIdStr));
        }
        deviceOpt.ifPresent(
                d -> {
                    UUID callerFarm = TenantContext.getFarmId();
                    if (callerFarm != null && !callerFarm.equals(d.getFarmId())) {
                        throw new SecurityException("Device does not belong to caller's farm");
                    }
                    d.setLastHeartbeat(Instant.now());
                    deviceRepo.save(d);
                });
    }
}
