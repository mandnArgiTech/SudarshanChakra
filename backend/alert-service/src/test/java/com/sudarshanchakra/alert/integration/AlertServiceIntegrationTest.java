package com.sudarshanchakra.alert.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.alert.dto.AlertPayload;
import com.sudarshanchakra.alert.model.Alert;
import com.sudarshanchakra.alert.repository.AlertRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

@Tag("integration")
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AlertServiceIntegrationTest extends AbstractAlertIntegrationTest {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void repository_saveAndQueryByPriority() {
        Alert a = Alert.builder()
                .nodeId("n1")
                .zoneId("z1")
                .zoneName("Zone")
                .zoneType("hazard")
                .priority("critical")
                .detectionClass("person")
                .confidence(0.9f)
                .build();
        alertRepository.save(a);
        assertThat(alertRepository.findByPriority("critical", org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent()).isNotEmpty();
    }

    @Test
    void api_getAlerts_returnsPage() throws Exception {
        alertRepository.save(Alert.builder()
                .nodeId("api-node")
                .zoneId("z")
                .zoneName("Z")
                .zoneType("hazard")
                .priority("warning")
                .detectionClass("x")
                .confidence(0.5f)
                .build());
        mockMvc.perform(get("/api/v1/alerts").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void api_acknowledgeAndResolve() throws Exception {
        Alert a = alertRepository.save(Alert.builder()
                .nodeId("n2")
                .zoneId("z2")
                .zoneName("Z2")
                .zoneType("hazard")
                .priority("high")
                .detectionClass("animal")
                .confidence(0.8f)
                .build());
        mockMvc.perform(patch("/api/v1/alerts/" + a.getId() + "/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("acknowledged"));

        mockMvc.perform(patch("/api/v1/alerts/" + a.getId() + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resolved"));
    }

    @Test
    void rabbit_criticalQueue_persistsAlert() throws Exception {
        String json = objectMapper.writeValueAsString(AlertPayload.builder()
                .nodeId("edge-1")
                .cameraId("cam-1")
                .zoneId("fence-a")
                .zoneName("North fence")
                .zoneType("intrusion")
                .priority("critical")
                .detectionClass("person")
                .confidence(0.95f)
                .build());
        rabbitTemplate.convertAndSend("farm.alerts", "farm.alerts.critical", json);

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                assertThat(alertRepository.findByNodeId("edge-1", org.springframework.data.domain.PageRequest.of(0, 5))
                        .getTotalElements()).isGreaterThan(0));
    }

    @Test
    void rabbit_waterLow_createsWarningAlert() {
        UUID tankId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO water_tanks (id, farm_id, name, threshold_low_pct) VALUES (?, ?::uuid, ?, ?)",
                tankId, "a0000000-0000-0000-0000-000000000001", "Test tank", 40.0);

        String msg = String.format(
                "{\"tank_id\":\"%s\",\"level_pct\":5.0,\"node_id\":\"esp-1\"}", tankId);
        rabbitTemplate.convertAndSend("farm.water", "water.level", msg);

        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
                assertThat(alertRepository.findFiltered(null, null, null,
                        org.springframework.data.domain.PageRequest.of(0, 20)).getContent())
                        .anyMatch(al -> "water_low".equals(al.getDetectionClass())));
    }
}
