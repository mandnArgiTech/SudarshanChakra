package com.sudarshanchakra.alert.service;

import com.sudarshanchakra.alert.model.AuditLog;
import com.sudarshanchakra.alert.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
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
}
