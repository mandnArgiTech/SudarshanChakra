package com.sudarshanchakra.device.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.device.model.EdgeNode;
import com.sudarshanchakra.device.service.DeviceService;
import org.junit.jupiter.api.Test;
import com.sudarshanchakra.device.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import com.sudarshanchakra.jwt.ResourceServerJwtAuthFilter;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EdgeNodeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
@WithMockUser(
        username = "t1",
        authorities = {"PERMISSION_devices:view", "PERMISSION_devices:manage"})
class EdgeNodeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    DeviceService deviceService;

    @MockBean
    @SuppressWarnings("unused")
    ResourceServerJwtAuthFilter resourceServerJwtAuthFilter;

    @Test
    void listNodes() throws Exception {
        UUID f = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        when(deviceService.getAllNodes()).thenReturn(List.of(
                EdgeNode.builder().id("n1").farmId(f).displayName("N1").build()));
        mockMvc.perform(get("/api/v1/nodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("n1"));
    }

    @Test
    void getNode() throws Exception {
        UUID f = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        when(deviceService.getNodeById("n1"))
                .thenReturn(EdgeNode.builder().id("n1").farmId(f).vpnIp("10.8.0.2").build());
        mockMvc.perform(get("/api/v1/nodes/n1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vpnIp").value("10.8.0.2"));
    }

    @Test
    void createNode() throws Exception {
        UUID f = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        EdgeNode saved = EdgeNode.builder().id("new-node").farmId(f).displayName("X").build();
        when(deviceService.createNode(any(EdgeNode.class))).thenReturn(saved);
        String body = objectMapper.writeValueAsString(saved);
        mockMvc.perform(post("/api/v1/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("new-node"));
    }

    @Test
    void updateNode() throws Exception {
        UUID f = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        EdgeNode upd = EdgeNode.builder().displayName("U").build();
        when(deviceService.updateNode(eq("n1"), any()))
                .thenReturn(EdgeNode.builder().id("n1").farmId(f).displayName("U").build());
        mockMvc.perform(put("/api/v1/nodes/n1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("U"));
    }

    @Test
    void getNode_notFound_returnsBadRequest() throws Exception {
        when(deviceService.getNodeById("missing"))
                .thenThrow(new IllegalArgumentException("Edge node not found: missing"));
        mockMvc.perform(get("/api/v1/nodes/missing"))
                .andExpect(status().isBadRequest());
    }
}
