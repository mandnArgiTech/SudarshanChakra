package com.sudarshanchakra.device.repository.water;

import com.sudarshanchakra.device.model.water.WaterTank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;

public interface WaterTankRepository extends JpaRepository<WaterTank, String> {
    List<WaterTank> findAllByOrderByLocationAscDisplayNameAsc();
    List<WaterTank> findByLocation(String location);

    @Modifying @Transactional
    @Query("UPDATE WaterTank t SET t.lastReadingAt = :ts, t.status = 'online' WHERE t.id = :id")
    void updateLastReading(@Param("id") String id, @Param("ts") OffsetDateTime ts);

    @Query(value = """
        SELECT DISTINCT t.id FROM water_tanks t
        JOIN water_tank_motor_map m ON m.tank_id = t.id
        WHERE m.motor_id = :motorId
        """, nativeQuery = true)
    List<String> findTankIdsByMotorId(@Param("motorId") String motorId);
}
