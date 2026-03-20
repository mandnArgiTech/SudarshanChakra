package com.sudarshanchakra.device.dto.water;

import lombok.Data;

/** PUT /api/v1/water/motors/{id} — partial update for thresholds and SMS config */
@Data
public class MotorUpdateRequest {
    private Boolean autoMode;
    private Double pumpOnPercent;
    private Double pumpOffPercent;
    private Integer maxRunMinutes;
    // SMS config — user-configurable Taro panel commands
    private String gsmTargetPhone;
    private String gsmOnMessage;
    private String gsmOffMessage;
}
