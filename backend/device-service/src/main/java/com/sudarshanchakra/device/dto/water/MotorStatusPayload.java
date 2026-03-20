package com.sudarshanchakra.device.dto.water;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Maps JSON published by ESP8266 motor_relay / motor_sms to {deviceTag}/motor/status. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MotorStatusPayload {
    @JsonProperty("state")       private String state;       // "running"|"stopped"|"pending"|"disabled"
    @JsonProperty("mode")        private String mode;        // "auto"|"on"|"off"
    @JsonProperty("runSeconds")  private Integer runSeconds;
    @JsonProperty("blocked")     private Boolean blocked;
    @JsonProperty("controlType") private String controlType; // "relay"|"sms"
    @JsonProperty("smsSent")     private Boolean smsSent;
    @JsonProperty("smsError")    private String smsError;
}
