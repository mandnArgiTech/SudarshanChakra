package com.sudarshanchakra.device.service.water;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.device.dto.water.MotorStatusPayload;
import com.sudarshanchakra.device.dto.water.WaterLevelPayload;
import com.sudarshanchakra.device.repository.water.WaterTankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * Consumes MQTT messages routed from RabbitMQ MQTT plugin.
 *
 * Topic routing (configured in rabbitmq_init.py):
 *   *.water.level   → water.level queue   → handleWaterLevel()
 *   *.motor.status  → motor.status queue  → handleMotorStatus()
 *
 * The MQTT topic {deviceTag}/water/level contains the tank deviceTag prefix.
 * We resolve tank ID via WaterTankRepository.findByDeviceTag(), or fall back
 * to extracting the deviceName portion from the payload.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaterMqttConsumer {

    private final WaterService waterService;
    private final WaterTankRepository tankRepo;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "water.level")
    public void handleWaterLevel(String message) {
        try {
            WaterLevelPayload payload = objectMapper.readValue(message, WaterLevelPayload.class);
            log.debug("water.level: device={} pct={}", payload.getDeviceName(), payload.getPercentFilled());

            // Resolve tank ID from deviceName in payload (matches config.mqtt.deviceName on ESP8266)
            String tankId = resolveTankId(payload.getDeviceName(), payload.getDeviceTag());
            if (tankId == null) {
                log.warn("Cannot resolve tank for deviceName={} deviceTag={} — ignoring",
                    payload.getDeviceName(), payload.getDeviceTag());
                return;
            }
            waterService.processLevelReading(tankId, payload);

        } catch (Exception e) {
            log.error("Failed to process water.level: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = "motor.status")
    public void handleMotorStatus(String message) {
        try {
            // Extract deviceTag from routing key or payload header
            // RabbitMQ MQTT plugin preserves topic in message properties
            MotorStatusPayload payload = objectMapper.readValue(message, MotorStatusPayload.class);
            // deviceTag will be in the payload or extracted from topic; use controlType as indicator
            log.debug("motor.status: state={} mode={} type={}", payload.getState(), payload.getMode(), payload.getControlType());
            // TODO: extract deviceTag from AMQP routing key (requires custom MessageListener)
        } catch (Exception e) {
            log.error("Failed to process motor.status: {}", e.getMessage(), e);
        }
    }

    /**
     * Resolve tank ID from MQTT payload fields.
     * Priority: deviceName field → deviceTag prefix match
     */
    private String resolveTankId(String deviceName, String deviceTag) {
        if (deviceName != null && !deviceName.isBlank()) {
            // deviceName e.g. "farm_tank1" — directly matches tank.id
            if (tankRepo.existsById(deviceName)) return deviceName;
        }
        if (deviceTag != null && !deviceTag.isBlank()) {
            // deviceTag e.g. "farm_tank1_a9ad51" — find tank by device_tag column
            return tankRepo.findAll().stream()
                .filter(t -> deviceTag.equals(t.getDeviceTag()))
                .map(t -> t.getId())
                .findFirst().orElse(null);
        }
        return null;
    }
}
