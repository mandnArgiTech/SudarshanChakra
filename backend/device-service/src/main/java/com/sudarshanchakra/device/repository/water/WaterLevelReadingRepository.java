package com.sudarshanchakra.device.repository.water;

import com.sudarshanchakra.device.model.water.WaterLevelReading;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface WaterLevelReadingRepository extends JpaRepository<WaterLevelReading, Long> {
    Optional<WaterLevelReading> findTopByTankIdOrderByCreatedAtDesc(String tankId);

    @Query("SELECT r FROM WaterLevelReading r WHERE r.tankId = :tankId AND r.createdAt >= :since ORDER BY r.createdAt ASC")
    List<WaterLevelReading> findByTankIdSince(@Param("tankId") String tankId, @Param("since") OffsetDateTime since);
}
