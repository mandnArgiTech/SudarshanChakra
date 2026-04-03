package com.sudarshanchakra.mdm.repository;

import com.sudarshanchakra.mdm.model.LocationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationHistoryRepository extends JpaRepository<LocationHistory, Long> {

    List<LocationHistory> findByDeviceIdAndRecordedAtBetweenOrderByRecordedAtDesc(
            UUID deviceId, Instant from, Instant to);

    List<LocationHistory> findByFarmIdAndRecordedAtAfterOrderByRecordedAtDesc(
            UUID farmId, Instant after);

    Optional<LocationHistory> findFirstByDeviceIdOrderByRecordedAtDesc(UUID deviceId);
}
