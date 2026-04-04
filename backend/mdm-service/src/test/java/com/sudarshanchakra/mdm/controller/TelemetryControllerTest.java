package com.sudarshanchakra.mdm.controller;

import com.sudarshanchakra.mdm.config.JwtAuthFilter;
import com.sudarshanchakra.mdm.config.JwtService;
import com.sudarshanchakra.mdm.service.TelemetryIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TelemetryController.class)
@AutoConfigureMockMvc(addFilters = false)
class TelemetryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    TelemetryIngestionService telemetryService;

    @MockBean
    JwtService jwtService;

    @MockBean
    JwtAuthFilter jwtAuthFilter;

    @Test
    void batch_validBody_returnsOk() throws Exception {
        when(telemetryService.processBatch(any())).thenReturn(Map.of(
                "status", "ok",
                "usage_records", 1,
                "call_records", 0,
                "screen_records", 1,
                "location_records", 0));

        String body = """
                {
                  "androidId": "abc123",
                  "appVersion": "2.1.0",
                  "appUsage": [{
                    "date": "2026-03-22",
                    "packageName": "com.whatsapp",
                    "appLabel": "WhatsApp",
                    "foregroundTimeSec": 3420,
                    "launchCount": 15,
                    "category": "social"
                  }],
                  "screenTime": {
                    "date": "2026-03-22",
                    "totalScreenTimeSec": 28800,
                    "unlockCount": 45
                  }
                }
                """;

        mockMvc.perform(post("/api/v1/mdm/telemetry/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void heartbeat_returnsOk() throws Exception {
        mockMvc.perform(post("/api/v1/mdm/telemetry/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"android_id\":\"abc123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void batch_invalidBody_returnsBadRequest() throws Exception {
        String body = """
                {
                  "androidId": "",
                  "appUsage": []
                }
                """;

        mockMvc.perform(post("/api/v1/mdm/telemetry/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
