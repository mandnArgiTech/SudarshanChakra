package com.sudarshanchakra.alert.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.ParamDef;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "farmId", type = UUID.class))
@Filter(
        name = "tenantFilter",
        condition = "node_id in (select en.id from edge_nodes en where en.farm_id = :farmId)")
public class Alert {

    @Id
    private UUID id;

    @Column(name = "node_id")
    private String nodeId;

    @Column(name = "camera_id")
    private String cameraId;

    @Column(name = "zone_id")
    private String zoneId;

    @Column(name = "zone_name")
    private String zoneName;

    @Column(name = "zone_type")
    private String zoneType;

    @Column(nullable = false)
    private String priority;

    @Column(name = "detection_class", nullable = false)
    private String detectionClass;

    private Float confidence;

    @Column(columnDefinition = "real[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private Float[] bbox;

    @Column(name = "snapshot_url")
    private String snapshotUrl;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "worker_suppressed")
    @Builder.Default
    private Boolean workerSuppressed = false;

    @Column(nullable = false)
    @Builder.Default
    private String status = "new";

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    private String notes;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
