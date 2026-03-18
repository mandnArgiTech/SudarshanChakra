package com.sudarshanchakra.alert.water;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WaterTankEntityRepository extends JpaRepository<WaterTankEntity, UUID> {
}
