package com.sudarshanchakra.device.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "zones")
@Filter(
        name = "tenantFilter",
        condition =
                "camera_id in (select c.id from cameras c join edge_nodes en on c.node_id = en.id where en.farm_id = :farmId)")
public class Zone {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "camera_id", length = 50)
    private String cameraId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "zone_type", nullable = false, length = 30)
    private String zoneType;

    @Column(nullable = false, length = 20)
    private String priority;

    @Column(name = "target_classes", columnDefinition = "text[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] targetClasses;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String polygon;

    @Column(length = 7)
    @Builder.Default
    private String color = "#FF0000";

    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "suppress_with_worker_tag")
    @Builder.Default
    private Boolean suppressWithWorkerTag = true;

    @Column(name = "dedup_window_seconds")
    @Builder.Default
    private Integer dedupWindowSeconds = 30;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
