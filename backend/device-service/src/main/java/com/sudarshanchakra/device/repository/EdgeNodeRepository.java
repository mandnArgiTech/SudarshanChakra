package com.sudarshanchakra.device.repository;

import com.sudarshanchakra.device.model.EdgeNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EdgeNodeRepository extends JpaRepository<EdgeNode, String> {

    List<EdgeNode> findByFarmId(UUID farmId);

    List<EdgeNode> findByStatus(String status);
}
