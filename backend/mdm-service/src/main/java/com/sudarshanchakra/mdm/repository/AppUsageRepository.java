package com.sudarshanchakra.mdm.repository;

import com.sudarshanchakra.mdm.model.AppUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUsageRepository extends JpaRepository<AppUsage, Long> {

    List<AppUsage> findByDeviceIdAndDateBetween(UUID deviceId, LocalDate from, LocalDate to);

    Optional<AppUsage> findByDeviceIdAndDateAndPackageName(UUID deviceId, LocalDate date, String packageName);
}
