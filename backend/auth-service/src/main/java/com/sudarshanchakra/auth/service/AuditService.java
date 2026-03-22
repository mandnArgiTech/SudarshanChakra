package com.sudarshanchakra.auth.service;

import com.sudarshanchakra.auth.model.AuditLog;
import com.sudarshanchakra.auth.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(UUID farmId, UUID userId, String action, String entityType, String entityId,
                    Map<String, Object> details, String ipAddress) {
        AuditLog row = AuditLog.builder()
                .farmId(farmId)
                .userId(userId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(row);
    }

    public Page<AuditLog> listForFarm(UUID farmId, String actionFilter, Pageable pageable) {
        if (actionFilter != null && !actionFilter.isBlank()) {
            return auditLogRepository.findByFarmIdAndActionOrderByCreatedAtDesc(farmId, actionFilter.trim(), pageable);
        }
        return auditLogRepository.findByFarmIdOrderByCreatedAtDesc(farmId, pageable);
    }
}
