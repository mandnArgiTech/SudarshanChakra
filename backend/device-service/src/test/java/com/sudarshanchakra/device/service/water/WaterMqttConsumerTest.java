package com.sudarshanchakra.device.service.water;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.device.dto.water.WaterLevelPayload;
import com.sudarshanchakra.device.model.water.WaterTank;
import com.sudarshanchakra.device.repository.water.WaterTankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaterMqttConsumerTest {

    @Mock
    private WaterService waterService;
    @Mock
    private WaterTankRepository tankRepo;

    private ObjectMapper objectMapper;
    private WaterMqttConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new WaterMqttConsumer(waterService, tankRepo, objectMapper);
    }

    @Test
    void handleWaterLevel_resolvesTankByDeviceNameAndCallsService() throws Exception {
        String json = """
            {"percentFilled":55.2,"deviceName":"tank-1","sensorOk":true}
            """;
        when(tankRepo.existsById("tank-1")).thenReturn(true);

        consumer.handleWaterLevel(json);

        ArgumentCaptor<WaterLevelPayload> cap = ArgumentCaptor.forClass(WaterLevelPayload.class);
        verify(waterService).processLevelReading(eq("tank-1"), cap.capture());
        assertThat(cap.getValue().getPercentFilled()).isEqualTo(55.2);
    }

    @Test
    void handleWaterLevel_resolvesTankByDeviceTag() throws Exception {
        String json = """
            {"percentFilled":30,"deviceTag":"mytag"}
            """;
        WaterTank t = WaterTank.builder()
            .id("tid")
            .farmId(UUID.randomUUID())
            .displayName("T")
            .deviceTag("mytag")
            .build();
        when(tankRepo.findAll()).thenReturn(List.of(t));

        consumer.handleWaterLevel(json);

        verify(waterService).processLevelReading(eq("tid"), any(WaterLevelPayload.class));
    }

    @Test
    void handleWaterLevel_unknownTank_skipsService() {
        String json = "{\"percentFilled\":10,\"deviceName\":\"missing\"}";
        when(tankRepo.existsById("missing")).thenReturn(false);

        consumer.handleWaterLevel(json);

        verify(waterService, never()).processLevelReading(any(), any(WaterLevelPayload.class));
    }

    @Test
    void handleWaterLevel_invalidJson_doesNotThrow() {
        consumer.handleWaterLevel("not-json");
        verify(waterService, never()).processLevelReading(any(), any(WaterLevelPayload.class));
    }
}
