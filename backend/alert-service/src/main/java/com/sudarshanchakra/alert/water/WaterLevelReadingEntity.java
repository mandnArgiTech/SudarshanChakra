package com.sudarshanchakra.alert.water;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "water_level_readings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterLevelReadingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tank_id", nullable = false)
    private UUID tankId;

    @Column(name = "level_pct", nullable = false)
    private Double levelPct;

    @Column(name = "raw_value")
    private Double rawValue;

    @Column(name = "node_id", length = 50)
    private String nodeId;

    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void pre() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
