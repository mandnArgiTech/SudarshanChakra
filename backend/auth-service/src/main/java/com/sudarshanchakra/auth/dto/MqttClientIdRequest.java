package com.sudarshanchakra.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MQTT client ID update request")
public class MqttClientIdRequest {

    @NotBlank(message = "MQTT client ID is required")
    @Schema(description = "MQTT client identifier for direct push notifications")
    private String mqttClientId;
}
