package com.sudarshanchakra.device.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "worker_tags")
@Filter(name = "tenantFilter", condition = "farm_id = :farmId")
public class WorkerTag {

    @Id
    @Column(name = "tag_id", length = 50)
    private String tagId;

    @Column(name = "worker_name", nullable = false, length = 100)
    private String workerName;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(length = 50)
    private String role;

    @Column(length = 20)
    private String phone;

    @Builder.Default
    private Boolean active = true;

    @Column(name = "last_seen")
    private OffsetDateTime lastSeen;

    @Column(name = "last_rssi")
    private Integer lastRssi;

    @Column(name = "last_node", length = 50)
    private String lastNode;

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
