package com.sudarshanchakra.device.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "water_level_readings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterLevelReading {

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

    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
