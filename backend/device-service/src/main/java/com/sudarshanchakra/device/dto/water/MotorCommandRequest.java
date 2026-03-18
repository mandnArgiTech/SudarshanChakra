package com.sudarshanchakra.device.dto.water;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MotorCommandRequest {
    @NotBlank
    @Pattern(regexp = "pump_on|pump_off|pump_auto", message = "command must be pump_on, pump_off, or pump_auto")
    private String command;
}
