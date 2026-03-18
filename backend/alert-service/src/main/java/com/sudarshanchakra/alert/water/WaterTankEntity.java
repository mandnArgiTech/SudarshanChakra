package com.sudarshanchakra.alert.water;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

@Entity
@Table(name = "water_tanks")
@Getter
public class WaterTankEntity {

    @Id
    private UUID id;

    @Column(name = "threshold_low_pct")
    private Double thresholdLowPct;
}
