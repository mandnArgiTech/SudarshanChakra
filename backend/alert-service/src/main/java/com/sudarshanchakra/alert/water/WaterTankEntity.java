package com.sudarshanchakra.alert.water;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * Read-only subset of {@code water_tanks} for water-level alert thresholds.
 * Schema must match {@code cloud/db/init.sql} and device-service {@code WaterTank}.
 */
@Entity
@Table(name = "water_tanks")
@Getter
public class WaterTankEntity {

    @Id
    @Column(length = 50)
    private String id;

    @Column(name = "low_threshold_percent")
    private Double lowThresholdPercent;
}
