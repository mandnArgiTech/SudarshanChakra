package com.sudarshanchakra.siren.service;

import com.sudarshanchakra.siren.config.RabbitMQConfig;
import com.sudarshanchakra.siren.dto.SirenRequest;
import com.sudarshanchakra.siren.dto.SirenResponse;
import com.sudarshanchakra.siren.model.SirenAction;
import com.sudarshanchakra.siren.repository.SirenActionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class SirenCommandService {

    private final RabbitTemplate rabbitTemplate;
    private final SirenActionRepository sirenActionRepository;

    @Autowired
    public SirenCommandService(@Autowired(required = false) RabbitTemplate rabbitTemplate,
                               SirenActionRepository sirenActionRepository) {
        this.rabbitTemplate = rabbitTemplate;
        this.sirenActionRepository = sirenActionRepository;
    }

    @Transactional
    public SirenResponse triggerSiren(SirenRequest request) {
        log.info("Triggering siren on node {} for alert {}", request.getNodeId(), request.getAlertId());

        Map<String, Object> command = new LinkedHashMap<>();
        command.put("action", "trigger");
        command.put("node_id", request.getNodeId());
        command.put("siren_url", request.getSirenUrl());
        command.put("alert_id", request.getAlertId() != null ? request.getAlertId().toString() : null);

        if (rabbitTemplate != null) {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_COMMANDS,
                        RabbitMQConfig.ROUTING_KEY_TRIGGER,
                        command);
                log.info("Siren trigger command published to RabbitMQ");
            } catch (Exception e) {
                log.error("Failed to publish siren trigger command: {}", e.getMessage(), e);
            }
        } else {
            log.warn("RabbitTemplate not available, skipping message publish");
        }

        SirenAction action = SirenAction.builder()
                .triggeredBySystem(true)
                .targetNode(request.getNodeId())
                .action("trigger")
                .alertId(request.getAlertId())
                .sirenUrl(request.getSirenUrl())
                .build();

        sirenActionRepository.save(action);
        log.info("Siren action audit log saved: {}", action.getId());

        return SirenResponse.builder()
                .status("triggered")
                .nodeId(request.getNodeId())
                .build();
    }

    @Transactional
    public SirenResponse stopSiren(SirenRequest request) {
        log.info("Stopping siren on node {}", request.getNodeId());

        Map<String, Object> command = new LinkedHashMap<>();
        command.put("action", "stop");
        command.put("node_id", request.getNodeId());

        if (rabbitTemplate != null) {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_COMMANDS,
                        RabbitMQConfig.ROUTING_KEY_STOP,
                        command);
                log.info("Siren stop command published to RabbitMQ");
            } catch (Exception e) {
                log.error("Failed to publish siren stop command: {}", e.getMessage(), e);
            }
        } else {
            log.warn("RabbitTemplate not available, skipping message publish");
        }

        SirenAction action = SirenAction.builder()
                .triggeredBySystem(true)
                .targetNode(request.getNodeId())
                .action("stop")
                .alertId(request.getAlertId())
                .sirenUrl(request.getSirenUrl())
                .build();

        sirenActionRepository.save(action);
        log.info("Siren stop action audit log saved: {}", action.getId());

        return SirenResponse.builder()
                .status("stopped")
                .nodeId(request.getNodeId())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<SirenAction> getHistory(Pageable pageable) {
        return sirenActionRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
