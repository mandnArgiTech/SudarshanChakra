package com.sudarshanchakra.mdm.repository;

import com.sudarshanchakra.mdm.model.CallLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CallLogRepository extends JpaRepository<CallLogEntry, Long> {

    List<CallLogEntry> findByDeviceIdAndCallTimestampBetween(UUID deviceId, Instant from, Instant to);
}
