package com.sudarshanchakra.mdm.service;

import com.sudarshanchakra.mdm.config.TenantContext;
import com.sudarshanchakra.mdm.dto.TelemetryBatchRequest;
import com.sudarshanchakra.mdm.model.AppUsage;
import com.sudarshanchakra.mdm.model.MdmDevice;
import com.sudarshanchakra.mdm.repository.AppUsageRepository;
import com.sudarshanchakra.mdm.repository.CallLogRepository;
import com.sudarshanchakra.mdm.repository.LocationHistoryRepository;
import com.sudarshanchakra.mdm.repository.MdmDeviceRepository;
import com.sudarshanchakra.mdm.repository.ScreenTimeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelemetryIngestionServiceTest {

    @Mock
    MdmDeviceRepository deviceRepo;

    @Mock
    AppUsageRepository appUsageRepository;

    @Mock
    CallLogRepository callLogRepository;

    @Mock
    ScreenTimeRepository screenTimeRepository;

    @Mock
    LocationHistoryRepository locationHistoryRepository;

    @InjectMocks
    TelemetryIngestionService service;

    UUID farmId;
    UUID deviceId;
    MdmDevice device;

    @BeforeEach
    void setUp() {
        farmId = UUID.randomUUID();
        deviceId = UUID.randomUUID();
        device = MdmDevice.builder()
                .id(deviceId)
                .farmId(farmId)
                .deviceName("Test Phone")
                .androidId("test-android-id")
                .status("active")
                .build();
        TenantContext.set(farmId, UUID.randomUUID(), List.of("mdm"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void processBatch_validData_savesAllRecords() {
        when(deviceRepo.findByAndroidId("test-android-id")).thenReturn(Optional.of(device));
        when(appUsageRepository.findByDeviceIdAndDateAndPackageName(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(deviceRepo.save(any(MdmDevice.class))).thenReturn(device);

        TelemetryBatchRequest req = new TelemetryBatchRequest(
                deviceId.toString(),
                "test-android-id",
                "2.1.0",
                List.of(new TelemetryBatchRequest.AppUsageDto(
                        "2026-03-22", "com.whatsapp", "WhatsApp", 3420, 15, "social")),
                List.of(new TelemetryBatchRequest.CallLogDto(
                        "****5678", "outgoing", "2026-03-22T10:15:00Z", 180, "Supplier")),
                new TelemetryBatchRequest.ScreenTimeDto("2026-03-22", 28800, 45),
                List.of(new TelemetryBatchRequest.LocationDto(
                        17.5, 78.2, 8f, 500f, 0f, 0f, "gps", 85, "2026-03-22T14:30:00Z"))
        );

        Map<String, Object> result = service.processBatch(req);

        assertThat(result.get("status")).isEqualTo("ok");
        assertThat(result.get("usage_records")).isEqualTo(1);
        assertThat(result.get("call_records")).isEqualTo(1);
        assertThat(result.get("screen_records")).isEqualTo(1);
        assertThat(result.get("location_records")).isEqualTo(1);
        verify(deviceRepo).save(any(MdmDevice.class));
        verify(appUsageRepository).save(any(AppUsage.class));
    }

    @Test
    void processBatch_unknownDevice_throws() {
        when(deviceRepo.findByAndroidId("unknown-id")).thenReturn(Optional.empty());

        TelemetryBatchRequest req = new TelemetryBatchRequest(
                null, "unknown-id", null, null, null, null, null);

        assertThatThrownBy(() -> service.processBatch(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown device");
    }

    @Test
    void processBatch_emptyBatch_onlyUpdatesSyncTime() {
        when(deviceRepo.findByAndroidId("test-android-id")).thenReturn(Optional.of(device));
        when(deviceRepo.save(any(MdmDevice.class))).thenReturn(device);

        TelemetryBatchRequest req = new TelemetryBatchRequest(
                deviceId.toString(), "test-android-id", null, null, null, null, null);

        Map<String, Object> result = service.processBatch(req);

        assertThat(result.get("status")).isEqualTo("ok");
        assertThat(result.get("usage_records")).isEqualTo(0);
        assertThat(result.get("call_records")).isEqualTo(0);
        assertThat(result.get("screen_records")).isEqualTo(0);
        assertThat(result.get("location_records")).isEqualTo(0);
        verify(deviceRepo).save(any(MdmDevice.class));
        verifyNoInteractions(appUsageRepository, callLogRepository, screenTimeRepository, locationHistoryRepository);
    }

    @Test
    void processBatch_duplicateUsage_upserts() {
        AppUsage existing = AppUsage.builder()
                .id(1L)
                .deviceId(deviceId)
                .farmId(farmId)
                .date(LocalDate.parse("2026-03-22"))
                .packageName("com.whatsapp")
                .appLabel("WhatsApp")
                .foregroundTimeSec(1000)
                .launchCount(5)
                .category("social")
                .build();

        when(deviceRepo.findByAndroidId("test-android-id")).thenReturn(Optional.of(device));
        when(appUsageRepository.findByDeviceIdAndDateAndPackageName(deviceId,
                LocalDate.parse("2026-03-22"), "com.whatsapp"))
                .thenReturn(Optional.of(existing));
        when(deviceRepo.save(any(MdmDevice.class))).thenReturn(device);

        TelemetryBatchRequest req = new TelemetryBatchRequest(
                deviceId.toString(), "test-android-id", null,
                List.of(new TelemetryBatchRequest.AppUsageDto(
                        "2026-03-22", "com.whatsapp", "WhatsApp", 5000, 20, "social")),
                null, null, null);

        Map<String, Object> result = service.processBatch(req);

        assertThat(result.get("usage_records")).isEqualTo(1);
        assertThat(existing.getForegroundTimeSec()).isEqualTo(5000);
        assertThat(existing.getLaunchCount()).isEqualTo(20);
        verify(appUsageRepository).save(existing);
    }

    @Test
    void recordHeartbeat_validAndroidId_updatesHeartbeat() {
        when(deviceRepo.findByAndroidId("test-android-id")).thenReturn(Optional.of(device));
        when(deviceRepo.save(any(MdmDevice.class))).thenReturn(device);

        service.recordHeartbeat(Map.of("android_id", "test-android-id"));

        assertThat(device.getLastHeartbeat()).isNotNull();
        verify(deviceRepo).save(device);
    }

    @Test
    void processBatch_wrongFarm_throwsSecurityException() {
        UUID otherFarm = UUID.randomUUID();
        TenantContext.set(otherFarm, UUID.randomUUID(), List.of("mdm"));

        when(deviceRepo.findByAndroidId("test-android-id")).thenReturn(Optional.of(device));

        TelemetryBatchRequest req = new TelemetryBatchRequest(
                deviceId.toString(), "test-android-id", null, null, null, null, null);

        assertThatThrownBy(() -> service.processBatch(req))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("does not belong");
    }
}
