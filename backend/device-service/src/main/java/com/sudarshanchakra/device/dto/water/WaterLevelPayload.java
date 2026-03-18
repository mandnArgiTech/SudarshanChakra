package com.sudarshanchakra.device.dto.water;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Maps the JSON published by ESP8266 sensor_only firmware to MQTT water/level topic. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WaterLevelPayload {
    @JsonProperty("percentFilled")    private Double percentFilled;
    @JsonProperty("percentRemaining") private Double percentRemaining;
    @JsonProperty("volumeLiters")     private Double volumeLiters;
    @JsonProperty("waterHeightMm")    private Double waterHeightMm;
    @JsonProperty("distanceMm")       private Double distanceMm;
    @JsonProperty("temperatureC")     private Double temperatureC;
    @JsonProperty("state")            private String state;
    @JsonProperty("sensorOk")         private Boolean sensorOk;
    @JsonProperty("deviceName")       private String deviceName;   // matches mqtt.deviceName config
    @JsonProperty("deviceTag")        private String deviceTag;
    @JsonProperty("timestamp")        private String timestamp;
}
