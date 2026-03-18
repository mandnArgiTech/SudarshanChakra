package com.sudarshanchakra.alert.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.alert.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertConsumerServiceTest {

    @Mock
    AlertRepository alertRepository;

    @Mock
    WebSocketService webSocketService;

    ObjectMapper objectMapper;
    AlertConsumerService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new AlertConsumerService(alertRepository, objectMapper, webSocketService);
    }

    private void invokeProcess(String json, String priority) throws Exception {
        Method m = AlertConsumerService.class.getDeclaredMethod("processAlert", String.class, String.class);
        m.setAccessible(true);
        m.invoke(service, json, priority);
    }

    @Test
    void processAlert_savesAndBroadcasts() throws Exception {
        String id = UUID.randomUUID().toString();
        String json = String.format(
                "{\"alert_id\":\"%s\",\"node_id\":\"n1\",\"camera_id\":\"c1\",\"zone_id\":\"z\","
                        + "\"zone_name\":\"Zn\",\"zone_type\":\"intrusion\",\"priority\":\"high\","
                        + "\"detection_class\":\"person\",\"confidence\":0.88}",
                id);
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        invokeProcess(json, "critical");

        verify(alertRepository).save(any());
        verify(webSocketService).broadcastAlert(any());
    }

    @Test
    void processAlert_invalidJson_noSave() throws Exception {
        invokeProcess("not-json{", "high");
        verify(alertRepository, never()).save(any());
        verify(webSocketService, never()).broadcastAlert(any());
    }

    @Test
    void processAlert_generatesIdWhenMissing() throws Exception {
        String json = "{\"node_id\":\"n1\",\"camera_id\":\"c1\",\"zone_id\":\"z\",\"zone_name\":\"Zn\","
                + "\"zone_type\":\"hazard\",\"detection_class\":\"snake\",\"confidence\":0.7}";
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        invokeProcess(json, "warning");
        verify(alertRepository).save(any());
        verify(webSocketService).broadcastAlert(any());
    }

    @Test
    void processAlert_usesQueuePriorityWhenMissing() throws Exception {
        String id = UUID.randomUUID().toString();
        String json = String.format(
                "{\"alert_id\":\"%s\",\"node_id\":\"n\",\"camera_id\":\"c\",\"zone_id\":\"z\","
                        + "\"zone_name\":\"Z\",\"zone_type\":\"x\",\"detection_class\":\"cow\",\"confidence\":0.9}",
                id);
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        invokeProcess(json, "high");
        verify(alertRepository).save(any());
    }

    @Test
    void processAlert_workerSuppressedField() throws Exception {
        String id = UUID.randomUUID().toString();
        String json = String.format(
                "{\"alert_id\":\"%s\",\"node_id\":\"n\",\"camera_id\":\"c\",\"zone_id\":\"z\","
                        + "\"zone_name\":\"Z\",\"zone_type\":\"intrusion\",\"detection_class\":\"person\","
                        + "\"confidence\":0.9,\"worker_suppressed\":true}",
                id);
        when(alertRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        invokeProcess(json, "high");
        verify(alertRepository).save(argThat(a -> Boolean.TRUE.equals(a.getWorkerSuppressed())));
    }
}
