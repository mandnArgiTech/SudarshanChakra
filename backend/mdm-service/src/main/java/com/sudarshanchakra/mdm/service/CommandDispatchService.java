package com.sudarshanchakra.mdm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.mdm.config.RabbitMQConfig;
import com.sudarshanchakra.mdm.config.TenantContext;
import com.sudarshanchakra.mdm.dto.CommandRequest;
import com.sudarshanchakra.mdm.model.MdmCommand;
import com.sudarshanchakra.mdm.model.MdmDevice;
import com.sudarshanchakra.mdm.repository.MdmCommandRepository;
import com.sudarshanchakra.mdm.repository.MdmDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommandDispatchService {

    private static final Set<String> VALID_COMMANDS = Set.of(
            "UPDATE_APP", "LOCK_SCREEN", "WIPE_DEVICE", "SYNC_TELEMETRY", "SET_POLICY"
    );

    private final MdmCommandRepository commandRepo;
    private final MdmDeviceRepository deviceRepo;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public MdmCommand dispatch(CommandRequest request) {
        if (!VALID_COMMANDS.contains(request.command())) {
            throw new IllegalArgumentException("Invalid command: " + request.command());
        }

        MdmDevice device = deviceRepo.findById(request.deviceId())
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + request.deviceId()));

        UUID callerFarm = TenantContext.getFarmId();
        if (callerFarm != null && !callerFarm.equals(device.getFarmId())) {
            throw new SecurityException("Device does not belong to caller's farm");
        }

        String payloadStr = null;
        if (request.payload() != null && !request.payload().isNull()) {
            payloadStr = request.payload().toString();
        }

        MdmCommand cmd = MdmCommand.builder()
                .deviceId(device.getId())
                .farmId(device.getFarmId())
                .command(request.command())
                .payload(payloadStr)
                .status("pending")
                .issuedBy(TenantContext.getUserId())
                .issuedAt(Instant.now())
                .build();
        cmd = commandRepo.save(cmd);

        String routingKey = "farm.mdm." + device.getId() + ".command";
        try {
            Map<String, Object> mqttPayload = Map.of(
                    "command_id", cmd.getId().toString(),
                    "command", cmd.getCommand(),
                    "payload", request.payload() != null && !request.payload().isNull() ? request.payload() : Map.of(),
                    "issued_at", cmd.getIssuedAt().toString()
            );
            String json = objectMapper.writeValueAsString(mqttPayload);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_MDM_COMMANDS, routingKey, json);
            log.info("Command {} dispatched to device {} via MQTT", cmd.getCommand(), device.getId());
        } catch (Exception e) {
            log.error("Failed to dispatch MQTT command: {}", e.getMessage());
            cmd.setStatus("failed");
            try {
                cmd.setResult(objectMapper.writeValueAsString(Map.of("error", e.getMessage())));
            } catch (JsonProcessingException ignored) {
                cmd.setResult("{\"error\":\"serialization_failed\"}");
            }
            commandRepo.save(cmd);
        }

        return cmd;
    }

    public List<MdmCommand> getHistory(UUID deviceId) {
        MdmDevice device = deviceRepo.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + deviceId));
        UUID callerFarm = TenantContext.getFarmId();
        if (callerFarm != null && !callerFarm.equals(device.getFarmId())) {
            throw new SecurityException("Device does not belong to caller's farm");
        }
        return commandRepo.findByDeviceIdOrderByIssuedAtDesc(deviceId);
    }

    @Transactional
    public void acknowledgeCommand(UUID commandId, boolean success, String resultJson) {
        commandRepo.findById(commandId).ifPresent(cmd -> {
            cmd.setStatus(success ? "executed" : "failed");
            cmd.setExecutedAt(Instant.now());
            cmd.setResult(resultJson);
            commandRepo.save(cmd);
        });
    }
}
