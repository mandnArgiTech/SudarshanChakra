package com.sudarshanchakra.device.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "edge_nodes")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "farmId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "farm_id = :farmId")
public class EdgeNode {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "vpn_ip", columnDefinition = "inet")
    private String vpnIp;

    @Column(name = "local_ip", columnDefinition = "inet")
    private String localIp;

    @Column(length = 20)
    @Builder.Default
    private String status = "unknown";

    @Column(name = "last_heartbeat")
    private OffsetDateTime lastHeartbeat;

    @Column(name = "hardware_info", columnDefinition = "jsonb")
    private String hardwareInfo;

    @Column(columnDefinition = "jsonb")
    private String config;

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
