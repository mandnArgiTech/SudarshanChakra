package com.sudarshanchakra.siren.repository;

import com.sudarshanchakra.siren.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
