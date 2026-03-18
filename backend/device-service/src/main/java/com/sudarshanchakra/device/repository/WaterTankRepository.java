package com.sudarshanchakra.device.repository;

import com.sudarshanchakra.device.model.WaterTank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WaterTankRepository extends JpaRepository<WaterTank, UUID> {
    List<WaterTank> findByFarmId(UUID farmId);
}
