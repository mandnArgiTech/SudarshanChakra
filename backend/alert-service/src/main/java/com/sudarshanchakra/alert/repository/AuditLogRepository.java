package com.sudarshanchakra.alert.repository;

import com.sudarshanchakra.alert.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
