package com.sudarshanchakra.mdm.repository;

import com.sudarshanchakra.mdm.model.ScreenTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScreenTimeRepository extends JpaRepository<ScreenTime, Long> {

    List<ScreenTime> findByDeviceIdAndDateBetween(UUID deviceId, LocalDate from, LocalDate to);

    Optional<ScreenTime> findByDeviceIdAndDate(UUID deviceId, LocalDate date);
}
