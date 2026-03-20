package com.sudarshanchakra.alert.service;

import com.sudarshanchakra.alert.dto.AlertResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WebSocketServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketService webSocketService;

    @Test
    void broadcastAlert_sendsToTopic() {
        UUID id = UUID.randomUUID();
        AlertResponse alert = AlertResponse.builder()
                .id(id)
                .nodeId("n1")
                .priority("high")
                .detectionClass("snake")
                .status("new")
                .build();

        webSocketService.broadcastAlert(alert);

        ArgumentCaptor<AlertResponse> cap = ArgumentCaptor.forClass(AlertResponse.class);
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/alerts"), cap.capture());
        assertThat(cap.getValue().getId()).isEqualTo(id);
    }

    @Test
    void broadcastAlert_preservesPayload() {
        AlertResponse alert = AlertResponse.builder()
                .id(UUID.randomUUID())
                .zoneName("Pond")
                .confidence(0.88f)
                .build();
        webSocketService.broadcastAlert(alert);
        verify(messagingTemplate).convertAndSend("/topic/alerts", alert);
    }
}
