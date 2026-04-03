package com.sudarshanchakra.mdm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record TelemetryBatchRequest(
        String deviceId,
        @NotBlank String androidId,
        String appVersion,
        @Valid List<AppUsageDto> appUsage,
        @Valid List<CallLogDto> callLogs,
        ScreenTimeDto screenTime,
        @Valid List<LocationDto> locations
) {

    public record AppUsageDto(
            @NotBlank String date,
            @NotBlank String packageName,
            String appLabel,
            int foregroundTimeSec,
            int launchCount,
            String category
    ) {
    }

    public record CallLogDto(
            String phoneNumberMasked,
            @NotBlank String callType,
            @NotBlank String callTimestamp,
            int durationSec,
            String contactName
    ) {
    }

    public record ScreenTimeDto(
            @NotBlank String date,
            int totalScreenTimeSec,
            int unlockCount
    ) {
    }

    public record LocationDto(
            double latitude,
            double longitude,
            Float accuracyMeters,
            Float altitudeMeters,
            Float speedMps,
            Float bearing,
            String provider,
            Integer batteryPercent,
            @NotBlank String recordedAt
    ) {
    }
}
