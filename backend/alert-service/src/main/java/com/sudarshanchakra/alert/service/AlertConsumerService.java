package com.sudarshanchakra.alert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.alert.dto.AlertPayload;
import com.sudarshanchakra.alert.dto.AlertResponse;
import com.sudarshanchakra.alert.model.Alert;
import com.sudarshanchakra.alert.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(ConnectionFactory.class)
public class AlertConsumerService {

    private final AlertRepository alertRepository;
    private final ObjectMapper objectMapper;
    private final WebSocketService webSocketService;

    @RabbitListener(queues = "alert.critical")
    public void handleCriticalAlert(String message) {
        processAlert(message, "critical");
    }

    @RabbitListener(queues = "alert.high")
    public void handleHighAlert(String message) {
        processAlert(message, "high");
    }

    @RabbitListener(queues = "alert.warning")
    public void handleWarningAlert(String message) {
        processAlert(message, "warning");
    }

    private void processAlert(String message, String priority) {
        try {
            log.info("Received {} alert: {}", priority, message);
            AlertPayload payload = objectMapper.readValue(message, AlertPayload.class);

            Alert alert = Alert.builder()
                    .id(payload.getAlertId() != null ? UUID.fromString(payload.getAlertId()) : UUID.randomUUID())
                    .nodeId(payload.getNodeId())
                    .cameraId(payload.getCameraId())
                    .zoneId(payload.getZoneId())
                    .zoneName(payload.getZoneName())
                    .zoneType(payload.getZoneType())
                    .priority(payload.getPriority() != null ? payload.getPriority() : priority)
                    .detectionClass(payload.getDetectionClass())
                    .confidence(payload.getConfidence())
                    .bbox(payload.getBbox() != null ? payload.getBbox().toArray(new Float[0]) : null)
                    .snapshotUrl(payload.getSnapshotUrl())
                    .workerSuppressed(payload.getWorkerSuppressed() != null ? payload.getWorkerSuppressed() : false)
                    .metadata(payload.getMetadata())
                    .build();

            Alert saved = alertRepository.save(alert);
            log.info("Saved alert {} with priority {}", saved.getId(), priority);

            webSocketService.broadcastAlert(AlertResponse.fromEntity(saved));
        } catch (Exception e) {
            log.error("Failed to process {} alert: {}", priority, e.getMessage(), e);
        }
    }
}
