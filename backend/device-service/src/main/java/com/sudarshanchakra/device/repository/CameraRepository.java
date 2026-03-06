package com.sudarshanchakra.device.repository;

import com.sudarshanchakra.device.model.Camera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CameraRepository extends JpaRepository<Camera, String> {

    List<Camera> findByNodeId(String nodeId);
}
