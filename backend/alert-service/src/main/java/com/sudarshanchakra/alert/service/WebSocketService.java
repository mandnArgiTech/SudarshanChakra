package com.sudarshanchakra.alert.service;

import com.sudarshanchakra.alert.dto.AlertResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastAlert(AlertResponse alert) {
        log.info("Broadcasting alert {} to /topic/alerts", alert.getId());
        messagingTemplate.convertAndSend("/topic/alerts", alert);
    }
}
