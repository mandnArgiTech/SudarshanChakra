package com.sudarshanchakra.device.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "water_tanks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterTank {

    @Id
    private UUID id;

    @NotNull
    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "capacity_liters")
    private Double capacityLiters;

    @Column(name = "threshold_low_pct")
    @Builder.Default
    private Double thresholdLowPct = 15.0;

    @Column(name = "mqtt_topic", length = 200)
    private String mqttTopic;

    @Column(name = "location_description")
    private String locationDescription;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
