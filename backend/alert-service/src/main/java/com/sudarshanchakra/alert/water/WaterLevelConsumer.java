package com.sudarshanchakra.alert.water;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.alert.dto.AlertResponse;
import com.sudarshanchakra.alert.model.Alert;
import com.sudarshanchakra.alert.repository.AlertRepository;
import com.sudarshanchakra.alert.service.WebSocketService;
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
            String tankId = n.path("tank_id").asText(null);
            if (tankId == null || tankId.isBlank()) {
                log.warn("water.level message missing tank_id");
                return;
            }

            double levelPct = n.hasNonNull("percent_filled")
                    ? n.get("percent_filled").asDouble()
                    : n.path("level_pct").asDouble(0.0);

            String nodeId = n.hasNonNull("node_id") ? n.get("node_id").asText() : null;

            WaterLevelReadingEntity.WaterLevelReadingEntityBuilder b = WaterLevelReadingEntity.builder()
                    .tankId(tankId)
                    .percentFilled(levelPct);

            if (n.hasNonNull("volume_liters")) {
                b.volumeLiters(n.get("volume_liters").asDouble());
            }
            if (n.hasNonNull("water_height_mm")) {
                b.waterHeightMm(n.get("water_height_mm").asDouble());
            }
            if (n.hasNonNull("distance_mm")) {
                b.distanceMm(n.get("distance_mm").asDouble());
            }
            if (n.hasNonNull("temperature_c")) {
                b.temperatureC(n.get("temperature_c").asDouble());
            }
            if (n.hasNonNull("state")) {
                b.state(n.get("state").asText());
            }
            if (n.has("sensor_ok")) {
                b.sensorOk(n.get("sensor_ok").asBoolean(true));
            }
            if (n.hasNonNull("battery_voltage")) {
                b.batteryVoltage(n.get("battery_voltage").asDouble());
            }
            if (n.hasNonNull("battery_percent")) {
                b.batteryPercent((short) n.get("battery_percent").asInt());
            }
            if (n.hasNonNull("battery_state")) {
                b.batteryState(n.get("battery_state").asText());
            }

            readingRepository.save(b.build());

            tankRepository.findById(tankId).ifPresent(tank -> {
                double th = tank.getLowThresholdPercent() != null ? tank.getLowThresholdPercent() : 15.0;
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
