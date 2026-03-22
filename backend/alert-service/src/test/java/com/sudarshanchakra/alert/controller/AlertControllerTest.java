package com.sudarshanchakra.alert.controller;

import com.sudarshanchakra.alert.dto.AlertResponse;
import com.sudarshanchakra.alert.service.AlertService;
import com.sudarshanchakra.jwt.ResourceServerJwtAuthFilter;
import org.junit.jupiter.api.Test;
import com.sudarshanchakra.alert.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
@WithMockUser(
        username = "t1",
        authorities = {
                "PERMISSION_alerts:view",
                "PERMISSION_alerts:acknowledge",
                "PERMISSION_alerts:resolve"
        })
class AlertControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AlertService alertService;

    @MockBean
    @SuppressWarnings("unused")
    ResourceServerJwtAuthFilter resourceServerJwtAuthFilter;

    @Test
    void createAlert_created() throws Exception {
        UUID id = UUID.randomUUID();
        var body = """
                {"alert_id":"%s","node_id":"n1","camera_id":"cam-1","zone_id":"z","zone_name":"Z","zone_type":"t",\
                "detection_class":"person","confidence":0.9,"priority":"high"}\
                """.formatted(id);
        when(alertService.createFromPayload(any())).thenAnswer(inv -> {
            com.sudarshanchakra.alert.dto.AlertPayload p = inv.getArgument(0);
            return AlertResponse.builder()
                    .id(UUID.fromString(p.getAlertId()))
                    .priority(p.getPriority())
                    .detectionClass(p.getDetectionClass())
                    .nodeId(p.getNodeId())
                    .status("new")
                    .build();
        });
        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.priority").value("high"));
        verify(alertService).createFromPayload(any());
    }

    @Test
    void getAlerts_ok() throws Exception {
        UUID id = UUID.randomUUID();
        when(alertService.getAlerts(isNull(), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(AlertResponse.builder().id(id).priority("high").build()),
                        PageRequest.of(0, 20), 1));
        mockMvc.perform(get("/api/v1/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()));
    }

    @Test
    void getAlert_byId() throws Exception {
        UUID id = UUID.randomUUID();
        when(alertService.getById(id))
                .thenReturn(AlertResponse.builder().id(id).detectionClass("person").build());
        mockMvc.perform(get("/api/v1/alerts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detectionClass").value("person"));
    }

    @Test
    void acknowledge_ok() throws Exception {
        UUID id = UUID.randomUUID();
        when(alertService.acknowledge(eq(id), any()))
                .thenReturn(AlertResponse.builder().id(id).status("acknowledged").build());
        mockMvc.perform(patch("/api/v1/alerts/" + id + "/acknowledge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"seen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("acknowledged"));
    }

    @Test
    void resolve_ok() throws Exception {
        UUID id = UUID.randomUUID();
        when(alertService.resolve(eq(id), any()))
                .thenReturn(AlertResponse.builder().id(id).status("resolved").build());
        mockMvc.perform(patch("/api/v1/alerts/" + id + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("resolved"));
    }

    @Test
    void falsePositive_ok() throws Exception {
        UUID id = UUID.randomUUID();
        when(alertService.markFalsePositive(eq(id), any()))
                .thenReturn(AlertResponse.builder().id(id).status("false_positive").build());
        mockMvc.perform(patch("/api/v1/alerts/" + id + "/false-positive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("false_positive"));
    }

    @Test
    void getAlerts_withFilters() throws Exception {
        when(alertService.getAlerts(eq("critical"), eq("new"), eq("node-a"), any()))
                .thenReturn(Page.empty());
        mockMvc.perform(get("/api/v1/alerts")
                        .param("priority", "critical")
                        .param("status", "new")
                        .param("nodeId", "node-a"))
                .andExpect(status().isOk());
    }
}
