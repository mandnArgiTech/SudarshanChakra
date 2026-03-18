package com.sudarshanchakra.device.service;

import com.sudarshanchakra.device.model.WaterLevelReading;
import com.sudarshanchakra.device.model.WaterTank;
import com.sudarshanchakra.device.repository.WaterLevelReadingRepository;
import com.sudarshanchakra.device.repository.WaterTankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WaterTankService {

    private final WaterTankRepository waterTankRepository;
    private final WaterLevelReadingRepository readingRepository;

    public List<WaterTank> listAll() {
        return waterTankRepository.findAll();
    }

    public List<WaterTank> listByFarm(UUID farmId) {
        return waterTankRepository.findByFarmId(farmId);
    }

    @Transactional
    public WaterTank create(WaterTank tank) {
        return waterTankRepository.save(tank);
    }

    public WaterTank getById(UUID id) {
        return waterTankRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Water tank not found: " + id));
    }

    public Page<WaterLevelReading> getHistory(UUID tankId, Pageable pageable) {
        getById(tankId);
        return readingRepository.findByTankIdOrderByCreatedAtDesc(tankId, pageable);
    }
}
