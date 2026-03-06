package com.sudarshanchakra.alert.service;

import com.sudarshanchakra.alert.dto.AlertResponse;
import com.sudarshanchakra.alert.dto.AlertUpdateRequest;
import com.sudarshanchakra.alert.model.Alert;
import com.sudarshanchakra.alert.model.AlertStatus;
import com.sudarshanchakra.alert.repository.AlertRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository alertRepository;

    @Transactional(readOnly = true)
    public Page<AlertResponse> getAlerts(String priority, String status, String nodeId, Pageable pageable) {
        Page<Alert> alerts = alertRepository.findFiltered(priority, status, nodeId, pageable);
        return alerts.map(AlertResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public AlertResponse getById(UUID id) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Alert not found: " + id));
        return AlertResponse.fromEntity(alert);
    }

    @Transactional
    public AlertResponse acknowledge(UUID id, AlertUpdateRequest request) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Alert not found: " + id));

        alert.setStatus(AlertStatus.ACKNOWLEDGED.getValue());
        alert.setAcknowledgedAt(OffsetDateTime.now());
        if (request != null && request.getNotes() != null) {
            alert.setNotes(request.getNotes());
        }

        Alert saved = alertRepository.save(alert);
        log.info("Alert {} acknowledged", id);
        return AlertResponse.fromEntity(saved);
    }

    @Transactional
    public AlertResponse resolve(UUID id, AlertUpdateRequest request) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Alert not found: " + id));

        alert.setStatus(AlertStatus.RESOLVED.getValue());
        alert.setResolvedAt(OffsetDateTime.now());
        if (request != null && request.getNotes() != null) {
            alert.setNotes(request.getNotes());
        }

        Alert saved = alertRepository.save(alert);
        log.info("Alert {} resolved", id);
        return AlertResponse.fromEntity(saved);
    }

    @Transactional
    public AlertResponse markFalsePositive(UUID id, AlertUpdateRequest request) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Alert not found: " + id));

        alert.setStatus(AlertStatus.FALSE_POSITIVE.getValue());
        alert.setResolvedAt(OffsetDateTime.now());
        if (request != null && request.getNotes() != null) {
            alert.setNotes(request.getNotes());
        }

        Alert saved = alertRepository.save(alert);
        log.info("Alert {} marked as false positive", id);
        return AlertResponse.fromEntity(saved);
    }
}
