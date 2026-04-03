package com.sudarshanchakra.mdm.service;

import com.sudarshanchakra.mdm.config.TenantContext;
import com.sudarshanchakra.mdm.model.AppUsage;
import com.sudarshanchakra.mdm.model.CallLogEntry;
import com.sudarshanchakra.mdm.model.MdmDevice;
import com.sudarshanchakra.mdm.model.ScreenTime;
import com.sudarshanchakra.mdm.repository.AppUsageRepository;
import com.sudarshanchakra.mdm.repository.CallLogRepository;
import com.sudarshanchakra.mdm.repository.MdmDeviceRepository;
import com.sudarshanchakra.mdm.repository.ScreenTimeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceManagementService {

    private final MdmDeviceRepository deviceRepo;
    private final AppUsageRepository appUsageRepository;
    private final CallLogRepository callLogRepository;
    private final ScreenTimeRepository screenTimeRepository;

    private UUID requireFarmId() {
        UUID farmId = TenantContext.getFarmId();
        if (farmId == null) {
            throw new IllegalArgumentException("Farm context required");
        }
        return farmId;
    }

    public List<MdmDevice> listByFarm() {
        return deviceRepo.findByFarmId(requireFarmId());
    }

    public MdmDevice getById(UUID id) {
        UUID farmId = requireFarmId();
        MdmDevice device = deviceRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + id));
        if (!device.getFarmId().equals(farmId)) {
            throw new SecurityException("Device does not belong to caller's farm");
        }
        return device;
    }

    @Transactional
    public MdmDevice register(MdmDevice device) {
        UUID farmId = requireFarmId();
        if (device.getAndroidId() == null || device.getAndroidId().isBlank()) {
            throw new IllegalArgumentException("androidId is required");
        }
        if (device.getDeviceName() == null || device.getDeviceName().isBlank()) {
            throw new IllegalArgumentException("deviceName is required");
        }
        String androidId = device.getAndroidId().trim();
        if (deviceRepo.findByAndroidId(androidId).isPresent()) {
            throw new IllegalArgumentException("Device with this androidId already exists: " + androidId);
        }

        MdmDevice toSave = MdmDevice.builder()
                .farmId(farmId)
                .userId(device.getUserId())
                .deviceName(device.getDeviceName().trim())
                .androidId(androidId)
                .model(device.getModel())
                .osVersion(device.getOsVersion())
                .appVersion(device.getAppVersion())
                .serialNumber(device.getSerialNumber())
                .imei(device.getImei())
                .phoneNumber(device.getPhoneNumber())
                .isDeviceOwner(Boolean.TRUE.equals(device.getIsDeviceOwner()))
                .isLockTaskActive(Boolean.TRUE.equals(device.getIsLockTaskActive()))
                .kioskPinHash(device.getKioskPinHash())
                .whitelistedApps(device.getWhitelistedApps())
                .policies(device.getPolicies())
                .locationIntervalSec(device.getLocationIntervalSec() != null ? device.getLocationIntervalSec() : 60)
                .mqttClientId(device.getMqttClientId())
                .status(device.getStatus() != null && !device.getStatus().isBlank() ? device.getStatus() : "pending")
                .provisionedAt(device.getProvisionedAt())
                .build();
        return deviceRepo.save(toSave);
    }

    @Transactional
    public MdmDevice update(UUID id, MdmDevice patch) {
        MdmDevice existing = getById(id);
        if (patch.getDeviceName() != null) {
            existing.setDeviceName(patch.getDeviceName());
        }
        if (patch.getUserId() != null) {
            existing.setUserId(patch.getUserId());
        }
        if (patch.getModel() != null) {
            existing.setModel(patch.getModel());
        }
        if (patch.getOsVersion() != null) {
            existing.setOsVersion(patch.getOsVersion());
        }
        if (patch.getAppVersion() != null) {
            existing.setAppVersion(patch.getAppVersion());
        }
        if (patch.getSerialNumber() != null) {
            existing.setSerialNumber(patch.getSerialNumber());
        }
        if (patch.getImei() != null) {
            existing.setImei(patch.getImei());
        }
        if (patch.getPhoneNumber() != null) {
            existing.setPhoneNumber(patch.getPhoneNumber());
        }
        if (patch.getIsDeviceOwner() != null) {
            existing.setIsDeviceOwner(patch.getIsDeviceOwner());
        }
        if (patch.getIsLockTaskActive() != null) {
            existing.setIsLockTaskActive(patch.getIsLockTaskActive());
        }
        if (patch.getKioskPinHash() != null) {
            existing.setKioskPinHash(patch.getKioskPinHash());
        }
        if (patch.getWhitelistedApps() != null) {
            existing.setWhitelistedApps(patch.getWhitelistedApps());
        }
        if (patch.getPolicies() != null) {
            existing.setPolicies(patch.getPolicies());
        }
        if (patch.getLocationIntervalSec() != null) {
            existing.setLocationIntervalSec(patch.getLocationIntervalSec());
        }
        if (patch.getMqttClientId() != null) {
            existing.setMqttClientId(patch.getMqttClientId());
        }
        if (patch.getStatus() != null) {
            existing.setStatus(patch.getStatus());
        }
        if (patch.getProvisionedAt() != null) {
            existing.setProvisionedAt(patch.getProvisionedAt());
        }
        return deviceRepo.save(existing);
    }

    @Transactional
    public MdmDevice decommission(UUID id) {
        MdmDevice existing = getById(id);
        existing.setStatus("decommissioned");
        return deviceRepo.save(existing);
    }

    public List<AppUsage> getUsage(UUID deviceId, String from, String to) {
        getById(deviceId);
        LocalDate fromD = LocalDate.parse(from);
        LocalDate toD = LocalDate.parse(to);
        return appUsageRepository.findByDeviceIdAndDateBetween(deviceId, fromD, toD);
    }

    public List<CallLogEntry> getCalls(UUID deviceId, String from, String to) {
        getById(deviceId);
        Instant fromI = Instant.parse(from);
        Instant toI = Instant.parse(to);
        return callLogRepository.findByDeviceIdAndCallTimestampBetween(deviceId, fromI, toI);
    }

    public List<ScreenTime> getScreenTime(UUID deviceId, String from, String to) {
        getById(deviceId);
        LocalDate fromD = LocalDate.parse(from);
        LocalDate toD = LocalDate.parse(to);
        return screenTimeRepository.findByDeviceIdAndDateBetween(deviceId, fromD, toD);
    }
}
