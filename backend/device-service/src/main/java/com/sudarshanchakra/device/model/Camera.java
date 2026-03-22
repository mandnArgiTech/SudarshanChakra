package com.sudarshanchakra.device.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cameras")
@Filter(
        name = "tenantFilter",
        condition = "node_id in (select en.id from edge_nodes en where en.farm_id = :farmId)")
public class Camera {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "node_id", length = 50)
    private String nodeId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "rtsp_url", nullable = false)
    private String rtspUrl;

    @Column(length = 100)
    private String model;

    @Column(name = "location_description")
    private String locationDescription;

    @Column(name = "fps_target")
    @Builder.Default
    private Float fpsTarget = 2.0f;

    @Column(length = 20)
    @Builder.Default
    private String resolution = "640x480";

    @Builder.Default
    private Boolean enabled = true;

    @Column(length = 20)
    @Builder.Default
    private String status = "unknown";

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
