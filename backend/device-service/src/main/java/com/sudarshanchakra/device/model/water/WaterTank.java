package com.sudarshanchakra.device.model.water;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "water_tanks")
public class WaterTank {

    @Id @Column(length = 50)
    private String id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "device_tag", length = 100)
    private String deviceTag;

    @Column(length = 50)
    private String location;        // "farm" | "home"

    @Column(name = "tank_type", length = 20)
    @Builder.Default private String tankType = "circular";

    @Column(name = "diameter_mm") private Double diameterMm;
    @Column(name = "height_mm")   private Double heightMm;
    @Column(name = "capacity_liters") private Double capacityLiters;
    @Column(name = "location_description") private String locationDescription;

    @Column(name = "low_threshold_percent")      @Builder.Default private Double lowThresholdPercent = 20.0;
    @Column(name = "critical_threshold_percent") @Builder.Default private Double criticalThresholdPercent = 10.0;
    @Column(name = "overflow_threshold_percent") @Builder.Default private Double overflowThresholdPercent = 95.0;

    @Column(length = 20) @Builder.Default private String status = "unknown";
    @Column(name = "last_reading_at") private OffsetDateTime lastReadingAt;

    @Column(name = "created_at", updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at") private OffsetDateTime updatedAt;

    @PrePersist  protected void onCreate() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate   protected void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
