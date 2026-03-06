package com.sudarshanchakra.alert.dto;

import com.sudarshanchakra.alert.model.Alert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertResponse {

    private UUID id;
    private String nodeId;
    private String cameraId;
    private String zoneId;
    private String zoneName;
    private String zoneType;
    private String priority;
    private String detectionClass;
    private Float confidence;
    private Float[] bbox;
    private String snapshotUrl;
    private String thumbnailUrl;
    private Boolean workerSuppressed;
    private String status;
    private UUID acknowledgedBy;
    private OffsetDateTime acknowledgedAt;
    private UUID resolvedBy;
    private OffsetDateTime resolvedAt;
    private String notes;
    private String metadata;
    private OffsetDateTime createdAt;

    public static AlertResponse fromEntity(Alert alert) {
        return AlertResponse.builder()
                .id(alert.getId())
                .nodeId(alert.getNodeId())
                .cameraId(alert.getCameraId())
                .zoneId(alert.getZoneId())
                .zoneName(alert.getZoneName())
                .zoneType(alert.getZoneType())
                .priority(alert.getPriority())
                .detectionClass(alert.getDetectionClass())
                .confidence(alert.getConfidence())
                .bbox(alert.getBbox())
                .snapshotUrl(alert.getSnapshotUrl())
                .thumbnailUrl(alert.getThumbnailUrl())
                .workerSuppressed(alert.getWorkerSuppressed())
                .status(alert.getStatus())
                .acknowledgedBy(alert.getAcknowledgedBy())
                .acknowledgedAt(alert.getAcknowledgedAt())
                .resolvedBy(alert.getResolvedBy())
                .resolvedAt(alert.getResolvedAt())
                .notes(alert.getNotes())
                .metadata(alert.getMetadata())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}
