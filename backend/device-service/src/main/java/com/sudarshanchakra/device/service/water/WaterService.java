package com.sudarshanchakra.device.service.water;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.device.dto.water.*;
import com.sudarshanchakra.device.model.water.*;
import com.sudarshanchakra.device.repository.water.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WaterService {

    private final WaterTankRepository tankRepo;
    private final WaterLevelReadingRepository readingRepo;
    private final WaterMotorRepository motorRepo;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    // ── Tank queries ─────────────────────────────────────────────────────────

    public List<WaterTankResponse> getAllTanks() {
        return tankRepo.findAllByOrderByLocationAscDisplayNameAsc().stream()
            .map(tank -> {
                var latest = readingRepo.findTopByTankIdOrderByCreatedAtDesc(tank.getId()).orElse(null);
                var motor  = motorRepo.findMotorForTank(tank.getId()).map(WaterMotorController::getId).orElse(null);
                return WaterTankResponse.from(tank, latest, motor);
            })
            .collect(Collectors.toList());
    }

    public WaterTankResponse getTank(String id) {
        var tank = tankRepo.findById(id).orElseThrow(() -> new NoSuchElementException("Tank not found: " + id));
        var latest = readingRepo.findTopByTankIdOrderByCreatedAtDesc(id).orElse(null);
        var motor  = motorRepo.findMotorForTank(id).map(WaterMotorController::getId).orElse(null);
        return WaterTankResponse.from(tank, latest, motor);
    }

    public List<WaterLevelReading> getHistory(String tankId, int hours) {
        OffsetDateTime since = OffsetDateTime.now().minusHours(hours);
        return readingRepo.findByTankIdSince(tankId, since);
    }

    // ── Water level consumer ─────────────────────────────────────────────────

    /**
     * Called by WaterLevelConsumer when a water/level MQTT message arrives.
     * 1. Saves reading. 2. Updates tank last_reading. 3. Evaluates auto-command.
     */
    @Transactional
    public void processLevelReading(String tankId, WaterLevelPayload payload) {
        // Save reading
        WaterLevelReading reading = WaterLevelReading.builder()
            .tankId(tankId)
            .percentFilled(payload.getPercentFilled())
            .volumeLiters(payload.getVolumeLiters())
            .waterHeightMm(payload.getWaterHeightMm())
            .distanceMm(payload.getDistanceMm())
            .temperatureC(payload.getTemperatureC())
            .state(payload.getState())
            .sensorOk(payload.getSensorOk() != null ? payload.getSensorOk() : true)
            .batteryVoltage(payload.getBattery() != null ? payload.getBattery().voltage : null)
            .batteryPercent(payload.getBattery() != null ? payload.getBattery().percent : null)
            .batteryState(payload.getBattery()  != null ? payload.getBattery().state   : null)
            .build();
        readingRepo.save(reading);
        tankRepo.updateLastReading(tankId, OffsetDateTime.now());
        log.debug("Saved reading for tank {} — {:.1f}%", tankId, payload.getPercentFilled());

        // Evaluate motor auto-command
        evaluateAutoCommand(tankId, payload.getPercentFilled());
    }

    /**
     * Cloud-side AUTO mode: decide whether to command the motor on/off
     * based on this tank's latest level and the motor's thresholds.
     */
    private void evaluateAutoCommand(String tankId, Double level) {
        if (level == null) return;
        motorRepo.findMotorForTank(tankId).ifPresent(motor -> {
            if (!Boolean.TRUE.equals(motor.getAutoMode())) return;

            boolean isRunning = "running".equals(motor.getState());
            boolean isStopped = "stopped".equals(motor.getState());

            if (isStopped && level < motor.getPumpOnPercent()) {
                log.info("AUTO: tank {} at {:.1f}% < {:.0f}% — commanding motor {} ON",
                    tankId, level, motor.getPumpOnPercent(), motor.getId());
                publishMotorCommand(motor, "pump_on", "auto");
            } else if (isRunning && level >= motor.getPumpOffPercent()) {
                log.info("AUTO: tank {} at {:.1f}% >= {:.0f}% — commanding motor {} OFF",
                    tankId, level, motor.getPumpOffPercent(), motor.getId());
                publishMotorCommand(motor, "pump_off", "auto");
            }
        });
    }

    // ── Motor commands ────────────────────────────────────────────────────────

    public void sendMotorCommand(String motorId, String command, String source) {
        WaterMotorController motor = motorRepo.findById(motorId)
            .orElseThrow(() -> new NoSuchElementException("Motor not found: " + motorId));
        publishMotorCommand(motor, command, source);
    }

    private void publishMotorCommand(WaterMotorController motor, String command, String source) {
        if (motor.getDeviceTag() == null || motor.getDeviceTag().isBlank()) {
            log.warn("Motor {} has no deviceTag — cannot publish command", motor.getId());
            return;
        }
        String topic = motor.getDeviceTag() + "/motor/command";
        String payload = String.format("{\"command\":\"%s\",\"source\":\"%s\"}", command, source);
        // Publish via MQTT (RabbitMQ MQTT plugin forwards to correct topic)
        rabbitTemplate.convertAndSend("amq.topic", topic.replace("/", "."), payload);
        log.info("Motor command published → {} : {}", topic, payload);
    }

    // ── Motor CRUD ────────────────────────────────────────────────────────────

    public List<WaterMotorController> getAllMotors() { return motorRepo.findAll(); }

    public WaterMotorController getMotor(String id) {
        return motorRepo.findById(id).orElseThrow(() -> new NoSuchElementException("Motor not found: " + id));
    }

    @Transactional
    public WaterMotorController updateMotor(String id, MotorUpdateRequest req) {
        WaterMotorController motor = getMotor(id);
        if (req.getAutoMode()        != null) motor.setAutoMode(req.getAutoMode());
        if (req.getPumpOnPercent()   != null) motor.setPumpOnPercent(req.getPumpOnPercent());
        if (req.getPumpOffPercent()  != null) motor.setPumpOffPercent(req.getPumpOffPercent());
        if (req.getMaxRunMinutes()   != null) motor.setMaxRunMinutes(req.getMaxRunMinutes());
        if (req.getGsmTargetPhone()  != null) motor.setGsmTargetPhone(req.getGsmTargetPhone());
        if (req.getGsmOnMessage()    != null) motor.setGsmOnMessage(req.getGsmOnMessage());
        if (req.getGsmOffMessage()   != null) motor.setGsmOffMessage(req.getGsmOffMessage());
        return motorRepo.save(motor);
    }

    // ── Motor status update (from MQTT) ───────────────────────────────────────

    @Transactional
    public void updateMotorStatus(String deviceTag, MotorStatusPayload payload) {
        motorRepo.findByDeviceTag(deviceTag).ifPresent(motor -> {
            if (payload.getState()      != null) motor.setState(payload.getState());
            if (payload.getMode()       != null) motor.setMode(payload.getMode());
            if (payload.getRunSeconds() != null) motor.setRunSeconds(payload.getRunSeconds());
            motor.setStatus("online");
            motor.setLastSeenAt(OffsetDateTime.now());
            motorRepo.save(motor);
            log.debug("Motor {} state={} mode={}", motor.getId(), motor.getState(), motor.getMode());
        });
    }
}
