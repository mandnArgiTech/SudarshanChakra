package com.sudarshanchakra.device.dto.water;

import com.sudarshanchakra.device.model.water.WaterLevelReading;
import com.sudarshanchakra.device.model.water.WaterTank;
import lombok.Builder;
import lombok.Data;
import java.time.OffsetDateTime;

@Data @Builder
public class WaterTankResponse {
    private String id;
    private String displayName;
    private String deviceTag;
    private String location;
    private String status;
    private Double capacityLiters;
    private Double lowThresholdPercent;
    private Double criticalThresholdPercent;
    private Double overflowThresholdPercent;
    private CurrentLevel currentLevel;
    private String linkedMotorId;

    @Data @Builder
    public static class CurrentLevel {
        private Double percentFilled;
        private Double volumeLiters;
        private Double waterHeightCm;
        private Double temperatureC;
        private String state;
        private OffsetDateTime lastReadingAt;
    }

    public static WaterTankResponse from(WaterTank tank, WaterLevelReading reading, String motorId) {
        CurrentLevel level = null;
        if (reading != null) {
            level = CurrentLevel.builder()
                .percentFilled(reading.getPercentFilled())
                .volumeLiters(reading.getVolumeLiters())
                .waterHeightCm(reading.getWaterHeightMm() != null ? reading.getWaterHeightMm() / 10.0 : null)
                .temperatureC(reading.getTemperatureC())
                .state(reading.getState())
                .lastReadingAt(reading.getCreatedAt())
                .build();
        }
        return WaterTankResponse.builder()
            .id(tank.getId()).displayName(tank.getDisplayName())
            .deviceTag(tank.getDeviceTag()).location(tank.getLocation())
            .status(tank.getStatus()).capacityLiters(tank.getCapacityLiters())
            .lowThresholdPercent(tank.getLowThresholdPercent())
            .criticalThresholdPercent(tank.getCriticalThresholdPercent())
            .overflowThresholdPercent(tank.getOverflowThresholdPercent())
            .currentLevel(level).linkedMotorId(motorId)
            .build();
    }
}
