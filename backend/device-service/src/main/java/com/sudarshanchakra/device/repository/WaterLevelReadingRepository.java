package com.sudarshanchakra.device.repository;

import com.sudarshanchakra.device.model.WaterLevelReading;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface WaterLevelReadingRepository extends JpaRepository<WaterLevelReading, Long> {
    Page<WaterLevelReading> findByTankIdOrderByCreatedAtDesc(UUID tankId, Pageable pageable);
}
