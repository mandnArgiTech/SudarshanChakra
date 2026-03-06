package com.sudarshanchakra.device.repository;

import com.sudarshanchakra.device.model.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, String> {

    List<Zone> findByCameraId(String cameraId);
}
