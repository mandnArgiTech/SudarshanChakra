package com.sudarshanchakra.alert.water;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.alert.model.Alert;
import com.sudarshanchakra.alert.repository.AlertRepository;
import com.sudarshanchakra.alert.service.WebSocketService;
import com.sudarshanchakra.alert.dto.AlertResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(ConnectionFactory.class)
public class WaterLevelConsumer {

    private final ObjectMapper objectMapper;
    private final WaterLevelReadingEntityRepository readingRepository;
    private final WaterTankEntityRepository tankRepository;
    private final AlertRepository alertRepository;
    private final WebSocketService webSocketService;

    @RabbitListener(queues = "water.level")
    @Transactional
    public void onLevelMessage(String message) {
        try {
            JsonNode n = objectMapper.readTree(message);
            UUID tankId = UUID.fromString(n.path("tank_id").asText());
            double levelPct = n.path("level_pct").asDouble();
            String nodeId = n.hasNonNull("node_id") ? n.get("node_id").asText() : null;
            Double raw = n.has("raw_value") ? n.get("raw_value").asDouble() : null;

            readingRepository.save(WaterLevelReadingEntity.builder()
                    .tankId(tankId)
                    .levelPct(levelPct)
                    .rawValue(raw)
                    .nodeId(nodeId)
                    .metadata(message.length() > 2000 ? message.substring(0, 2000) : message)
                    .build());

            tankRepository.findById(tankId).ifPresent(tank -> {
                double th = tank.getThresholdLowPct() != null ? tank.getThresholdLowPct() : 15.0;
                if (levelPct < th) {
                    Alert alert = Alert.builder()
                            .id(UUID.randomUUID())
                            .nodeId(nodeId)
                            .cameraId(null)
                            .zoneId("water-tank")
                            .zoneName("Water level low")
                            .zoneType("hazard")
                            .priority("warning")
                            .detectionClass("water_low")
                            .confidence(1.0f)
                            .metadata("{\"tank_id\":\"" + tankId + "\",\"level_pct\":" + levelPct + "}")
                            .build();
                    alertRepository.save(alert);
                    webSocketService.broadcastAlert(AlertResponse.fromEntity(alert));
                    log.warn("Water low alert: tank {} at {}% (threshold {}%)", tankId, levelPct, th);
                }
            });
        } catch (Exception e) {
            log.error("water.level message failed: {}", e.getMessage(), e);
        }
    }
}
