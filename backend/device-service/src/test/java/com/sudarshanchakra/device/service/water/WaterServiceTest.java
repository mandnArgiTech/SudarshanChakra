package com.sudarshanchakra.device.service.water;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.device.dto.water.MotorStatusPayload;
import com.sudarshanchakra.device.dto.water.MotorUpdateRequest;
import com.sudarshanchakra.device.dto.water.WaterLevelPayload;
import com.sudarshanchakra.device.dto.water.WaterTankResponse;
import com.sudarshanchakra.device.model.water.WaterLevelReading;
import com.sudarshanchakra.device.model.water.WaterMotorController;
import com.sudarshanchakra.device.model.water.WaterTank;
import com.sudarshanchakra.device.repository.water.WaterLevelReadingRepository;
import com.sudarshanchakra.device.repository.water.WaterMotorRepository;
import com.sudarshanchakra.device.repository.water.WaterTankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaterServiceTest {

    @Mock
    private WaterTankRepository tankRepo;
    @Mock
    private WaterLevelReadingRepository readingRepo;
    @Mock
    private WaterMotorRepository motorRepo;
    @Mock
    private ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private WaterService waterService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        waterService = new WaterService(tankRepo, readingRepo, motorRepo, rabbitTemplateProvider, objectMapper);
    }

    @Test
    void getAllTanks_mapsMotorAndReading() {
        WaterTank tank = WaterTank.builder().id("t1").farmId(UUID.randomUUID()).displayName("Main").build();
        when(tankRepo.findAllByOrderByLocationAscDisplayNameAsc()).thenReturn(List.of(tank));
        when(readingRepo.findTopByTankIdOrderByCreatedAtDesc("t1")).thenReturn(Optional.empty());
        when(motorRepo.findMotorForTank("t1")).thenReturn(Optional.empty());

        List<WaterTankResponse> out = waterService.getAllTanks();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).getId()).isEqualTo("t1");
        assertThat(out.get(0).getDisplayName()).isEqualTo("Main");
    }

    @Test
    void getTank_notFound_throws() {
        when(tankRepo.findById("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> waterService.getTank("x")).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getHistory_delegatesToRepository() {
        when(readingRepo.findByTankIdSince(eq("t1"), any())).thenReturn(List.of());
        assertThat(waterService.getHistory("t1", 24)).isEmpty();
        verify(readingRepo).findByTankIdSince(eq("t1"), any());
    }

    @Test
    void sendMotorCommand_noRabbitTemplate_logsOnly() {
        WaterMotorController motor = WaterMotorController.builder()
            .id("m1")
            .farmId(UUID.randomUUID())
            .displayName("Pump")
            .controlType("relay")
            .deviceTag("farm/pump1")
            .build();
        when(motorRepo.findById("m1")).thenReturn(Optional.of(motor));
        when(rabbitTemplateProvider.getIfAvailable()).thenReturn(null);

        waterService.sendMotorCommand("m1", "pump_on", "test");

        verify(rabbitTemplateProvider).getIfAvailable();
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(String.class));
    }

    @Test
    void sendMotorCommand_publishesWhenTemplateAvailable() {
        WaterMotorController motor = WaterMotorController.builder()
            .id("m1")
            .farmId(UUID.randomUUID())
            .displayName("Pump")
            .controlType("relay")
            .deviceTag("farm/pump1")
            .build();
        when(motorRepo.findById("m1")).thenReturn(Optional.of(motor));
        when(rabbitTemplateProvider.getIfAvailable()).thenReturn(rabbitTemplate);

        waterService.sendMotorCommand("m1", "pump_off", "app");

        verify(rabbitTemplate).convertAndSend(eq("amq.topic"), eq("farm.pump1.motor.command"), any(String.class));
    }

    @Test
    void processLevelReading_savesAndTriggersAutoOnWhenStoppedAndLowLevel() {
        WaterLevelPayload payload = new WaterLevelPayload();
        payload.setPercentFilled(15.0);
        payload.setDeviceName("tank-a");

        WaterMotorController motor = WaterMotorController.builder()
            .id("mot1")
            .farmId(UUID.randomUUID())
            .displayName("P")
            .controlType("relay")
            .deviceTag("dev/tag")
            .autoMode(true)
            .state("stopped")
            .pumpOnPercent(20.0)
            .pumpOffPercent(85.0)
            .build();

        when(readingRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(motorRepo.findMotorForTank("tank-a")).thenReturn(Optional.of(motor));
        when(rabbitTemplateProvider.getIfAvailable()).thenReturn(rabbitTemplate);

        waterService.processLevelReading("tank-a", payload);

        verify(readingRepo).save(any(WaterLevelReading.class));
        verify(tankRepo).updateLastReading(eq("tank-a"), any());
        verify(rabbitTemplate).convertAndSend(eq("amq.topic"), eq("dev.tag.motor.command"), any(String.class));
    }

    @Test
    void updateMotorStatus_updatesFields() {
        MotorStatusPayload p = new MotorStatusPayload();
        p.setState("running");
        p.setMode("auto");
        p.setRunSeconds(42);

        WaterMotorController motor = WaterMotorController.builder()
            .id("m1")
            .farmId(UUID.randomUUID())
            .displayName("P")
            .controlType("relay")
            .deviceTag("t")
            .build();

        when(motorRepo.findByDeviceTag("tag1")).thenReturn(Optional.of(motor));
        when(motorRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        waterService.updateMotorStatus("tag1", p);

        ArgumentCaptor<WaterMotorController> cap = ArgumentCaptor.forClass(WaterMotorController.class);
        verify(motorRepo).save(cap.capture());
        assertThat(cap.getValue().getState()).isEqualTo("running");
        assertThat(cap.getValue().getMode()).isEqualTo("auto");
        assertThat(cap.getValue().getRunSeconds()).isEqualTo(42);
        assertThat(cap.getValue().getStatus()).isEqualTo("online");
    }

    @Test
    void updateMotor_appliesRequestFields() {
        MotorUpdateRequest req = new MotorUpdateRequest();
        req.setAutoMode(false);
        req.setPumpOnPercent(10.0);

        WaterMotorController motor = WaterMotorController.builder()
            .id("m1")
            .farmId(UUID.randomUUID())
            .displayName("P")
            .controlType("relay")
            .build();

        when(motorRepo.findById("m1")).thenReturn(Optional.of(motor));
        when(motorRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WaterMotorController out = waterService.updateMotor("m1", req);
        assertThat(out.getAutoMode()).isFalse();
        assertThat(out.getPumpOnPercent()).isEqualTo(10.0);
    }
}
