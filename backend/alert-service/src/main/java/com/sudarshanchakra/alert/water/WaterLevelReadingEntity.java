package com.sudarshanchakra.alert.water;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * Same table/columns as device-service {@code WaterLevelReading} and {@code cloud/db/init.sql}.
 */
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

    @Column(name = "tank_id", length = 50, nullable = false)
    private String tankId;

    @Column(name = "percent_filled", nullable = false)
    private Double percentFilled;

    @Column(name = "volume_liters")
    private Double volumeLiters;

    @Column(name = "water_height_mm")
    private Double waterHeightMm;

    @Column(name = "distance_mm")
    private Double distanceMm;

    @Column(name = "temperature_c")
    private Double temperatureC;

    @Column(length = 20)
    private String state;

    @Column(name = "sensor_ok")
    @Builder.Default
    private Boolean sensorOk = true;

    @Column(name = "battery_voltage")
    private Double batteryVoltage;

    /** DB column is SMALLINT (int2); @JdbcTypeCode avoids Hibernate 6 validating as INTEGER. */
    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "battery_percent")
    private Short batteryPercent;

    @Column(name = "battery_state", length = 10)
    private String batteryState;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void pre() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
