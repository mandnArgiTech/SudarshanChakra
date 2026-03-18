package com.sudarshanchakra.device.repository.water;

import com.sudarshanchakra.device.model.water.WaterMotorController;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface WaterMotorRepository extends JpaRepository<WaterMotorController, String> {
    List<WaterMotorController> findByLocation(String location);
    Optional<WaterMotorController> findByDeviceTag(String deviceTag);

    @Query(value = """
        SELECT mc.* FROM water_motor_controllers mc
        JOIN water_tank_motor_map m ON m.motor_id = mc.id
        WHERE m.tank_id = :tankId
        LIMIT 1
        """, nativeQuery = true)
    Optional<WaterMotorController> findMotorForTank(@Param("tankId") String tankId);
}
