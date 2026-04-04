package com.sudarshanchakra.mdm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.mdm.config.RabbitMQConfig;
import com.sudarshanchakra.mdm.config.TenantContext;
import com.sudarshanchakra.mdm.dto.CommandRequest;
import com.sudarshanchakra.mdm.model.MdmCommand;
import com.sudarshanchakra.mdm.model.MdmDevice;
import com.sudarshanchakra.mdm.repository.MdmCommandRepository;
import com.sudarshanchakra.mdm.repository.MdmDeviceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandDispatchServiceTest {

    @Mock
    MdmCommandRepository commandRepo;

    @Mock
    MdmDeviceRepository deviceRepo;

    @Mock
    RabbitTemplate rabbitTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    CommandDispatchService service;

    UUID farmId;
    UUID deviceId;
    MdmDevice device;

    @BeforeEach
    void setUp() {
        service = new CommandDispatchService(commandRepo, deviceRepo, rabbitTemplate, objectMapper);
        farmId = UUID.randomUUID();
        deviceId = UUID.randomUUID();
        device = MdmDevice.builder()
                .id(deviceId)
                .farmId(farmId)
                .deviceName("Test Phone")
                .androidId("abc123")
                .status("active")
                .build();
        TenantContext.set(farmId, UUID.randomUUID(), List.of("mdm"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void dispatch_validCommand_savesAndPublishes() {
        when(deviceRepo.findById(deviceId)).thenReturn(Optional.of(device));
        when(commandRepo.save(any(MdmCommand.class))).thenAnswer(i -> {
            MdmCommand cmd = i.getArgument(0);
            cmd.setId(UUID.randomUUID());
            return cmd;
        });

        CommandRequest req = new CommandRequest(deviceId, "SYNC_TELEMETRY", null);
        MdmCommand result = service.dispatch(req);

        assertThat(result.getCommand()).isEqualTo("SYNC_TELEMETRY");
        assertThat(result.getDeviceId()).isEqualTo(deviceId);
        assertThat(result.getStatus()).isEqualTo("pending");

        String expectedRoutingKey = "farm.mdm." + deviceId + ".command";
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_MDM_COMMANDS),
                eq(expectedRoutingKey),
                any(String.class));
        verify(commandRepo).save(any(MdmCommand.class));
    }

    @Test
    void dispatch_invalidCommandName_throws() {
        CommandRequest req = new CommandRequest(deviceId, "INVALID_CMD", null);

        assertThatThrownBy(() -> service.dispatch(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid command");
    }

    @Test
    void dispatch_deviceNotFound_throws() {
        when(deviceRepo.findById(deviceId)).thenReturn(Optional.empty());

        CommandRequest req = new CommandRequest(deviceId, "SYNC_TELEMETRY", null);

        assertThatThrownBy(() -> service.dispatch(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Device not found");
    }

    @Test
    void dispatch_mqttPublishFailure_setsStatusFailed() {
        when(deviceRepo.findById(deviceId)).thenReturn(Optional.of(device));
        when(commandRepo.save(any(MdmCommand.class))).thenAnswer(i -> {
            MdmCommand cmd = i.getArgument(0);
            if (cmd.getId() == null) cmd.setId(UUID.randomUUID());
            return cmd;
        });
        doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(String.class));

        CommandRequest req = new CommandRequest(deviceId, "LOCK_SCREEN", null);
        MdmCommand result = service.dispatch(req);

        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getResult()).contains("broker down");

        ArgumentCaptor<MdmCommand> captor = ArgumentCaptor.forClass(MdmCommand.class);
        verify(commandRepo, times(2)).save(captor.capture());
        MdmCommand lastSaved = captor.getAllValues().get(1);
        assertThat(lastSaved.getStatus()).isEqualTo("failed");
    }

    @Test
    void dispatch_wrongFarm_throwsSecurityException() {
        UUID otherFarm = UUID.randomUUID();
        TenantContext.set(otherFarm, UUID.randomUUID(), List.of("mdm"));

        when(deviceRepo.findById(deviceId)).thenReturn(Optional.of(device));

        CommandRequest req = new CommandRequest(deviceId, "SYNC_TELEMETRY", null);

        assertThatThrownBy(() -> service.dispatch(req))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("does not belong");
    }
}
