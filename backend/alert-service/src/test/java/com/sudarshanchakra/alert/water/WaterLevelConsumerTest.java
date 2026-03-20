package com.sudarshanchakra.alert.water;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.alert.repository.AlertRepository;
import com.sudarshanchakra.alert.service.WebSocketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaterLevelConsumerTest {

    @Mock
    private WaterLevelReadingEntityRepository readingRepository;

    @Mock
    private WaterTankEntityRepository tankRepository;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private WebSocketService webSocketService;

    private ObjectMapper objectMapper = new ObjectMapper();

    private WaterLevelConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new WaterLevelConsumer(
                objectMapper,
                readingRepository,
                tankRepository,
                alertRepository,
                webSocketService);
    }

    @Test
    void onLevelMessage_belowThreshold_createsAlert() throws Exception {
        UUID tankId = UUID.randomUUID();
        WaterTankEntity tank = new WaterTankEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(tank, "id", tankId);
        org.springframework.test.util.ReflectionTestUtils.setField(tank, "thresholdLowPct", 40.0);

        when(tankRepository.findById(tankId)).thenReturn(Optional.of(tank));

        String msg = String.format(
                "{\"tank_id\":\"%s\",\"level_pct\":5.0,\"node_id\":\"esp-1\"}", tankId);
        consumer.onLevelMessage(msg);

        verify(readingRepository).save(any(WaterLevelReadingEntity.class));
        verify(alertRepository).save(any());
        verify(webSocketService).broadcastAlert(any());
    }

    @Test
    void onLevelMessage_aboveThreshold_noAlert() throws Exception {
        UUID tankId = UUID.randomUUID();
        WaterTankEntity tank = new WaterTankEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(tank, "id", tankId);
        org.springframework.test.util.ReflectionTestUtils.setField(tank, "thresholdLowPct", 15.0);

        when(tankRepository.findById(tankId)).thenReturn(Optional.of(tank));

        String msg = String.format("{\"tank_id\":\"%s\",\"level_pct\":80.0}", tankId);
        consumer.onLevelMessage(msg);

        verify(readingRepository).save(any(WaterLevelReadingEntity.class));
        verify(alertRepository, never()).save(any());
        verify(webSocketService, never()).broadcastAlert(any());
    }

    @Test
    void onLevelMessage_invalidJson_doesNotThrow() {
        consumer.onLevelMessage("not-json");
        verify(readingRepository, never()).save(any());
    }
}
