package com.sudarshanchakra.auth.repository;

import com.sudarshanchakra.auth.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByFarmIdOrderByCreatedAtDesc(UUID farmId, Pageable pageable);

    Page<AuditLog> findByFarmIdAndActionOrderByCreatedAtDesc(UUID farmId, String action, Pageable pageable);
}
