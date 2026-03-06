package com.sudarshanchakra.device.repository;

import com.sudarshanchakra.device.model.WorkerTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkerTagRepository extends JpaRepository<WorkerTag, String> {

    List<WorkerTag> findByFarmId(UUID farmId);
}
