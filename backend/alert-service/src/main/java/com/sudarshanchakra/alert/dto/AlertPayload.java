package com.sudarshanchakra.alert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertPayload {

    @JsonProperty("alert_id")
    private String alertId;

    @JsonProperty("node_id")
    private String nodeId;

    @JsonProperty("camera_id")
    private String cameraId;

    @JsonProperty("zone_id")
    private String zoneId;

    @JsonProperty("zone_name")
    private String zoneName;

    @JsonProperty("zone_type")
    private String zoneType;

    private String priority;

    @JsonProperty("detection_class")
    private String detectionClass;

    private Float confidence;

    private List<Float> bbox;

    @JsonProperty("snapshot_url")
    private String snapshotUrl;

    @JsonProperty("worker_suppressed")
    private Boolean workerSuppressed;

    private String timestamp;

    private String metadata;
}
