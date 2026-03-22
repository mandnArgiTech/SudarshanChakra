package com.sudarshanchakra.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private Long id;
    private UUID farmId;
    private UUID userId;
    private String action;
    private String entityType;
    private String entityId;
    private Map<String, Object> details;
    private String ipAddress;
    private OffsetDateTime createdAt;
}
