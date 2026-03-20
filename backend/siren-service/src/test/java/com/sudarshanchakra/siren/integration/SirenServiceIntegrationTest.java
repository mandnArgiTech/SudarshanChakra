package com.sudarshanchakra.siren.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SirenServiceIntegrationTest extends AbstractSirenIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void trigger_persistsAndHistoryLists() throws Exception {
        String body = "{\"nodeId\":\"node-s1\",\"sirenUrl\":\"http://10.0.0.5/trigger\",\"alertId\":\"" + UUID.randomUUID() + "\"}";
        mockMvc.perform(post("/api/v1/siren/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("triggered"))
                .andExpect(jsonPath("$.nodeId").value("node-s1"));

        mockMvc.perform(get("/api/v1/siren/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("trigger"))
                .andExpect(jsonPath("$.content[0].targetNode").value("node-s1"));
    }

    @Test
    void stop_persists() throws Exception {
        mockMvc.perform(post("/api/v1/siren/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodeId\":\"node-s2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("stopped"));

        mockMvc.perform(get("/api/v1/siren/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.targetNode=='node-s2')]").exists());
    }
}
