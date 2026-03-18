package com.sudarshanchakra.siren.controller;

import com.sudarshanchakra.siren.dto.SirenRequest;
import com.sudarshanchakra.siren.dto.SirenResponse;
import com.sudarshanchakra.siren.model.SirenAction;
import com.sudarshanchakra.siren.service.SirenCommandService;
import org.junit.jupiter.api.Test;
import com.sudarshanchakra.siren.config.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SirenController.class)
@Import(SecurityConfig.class)
class SirenControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    SirenCommandService sirenCommandService;

    @Test
    void trigger_ok() throws Exception {
        when(sirenCommandService.triggerSiren(any(SirenRequest.class)))
                .thenReturn(SirenResponse.builder().status("triggered").nodeId("n1").build());
        mockMvc.perform(post("/api/v1/siren/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodeId\":\"n1\",\"sirenUrl\":\"http://x\",\"alertId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("triggered"));
    }

    @Test
    void stop_ok() throws Exception {
        when(sirenCommandService.stopSiren(any()))
                .thenReturn(SirenResponse.builder().status("stopped").nodeId("n1").build());
        mockMvc.perform(post("/api/v1/siren/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nodeId\":\"n1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("stopped"));
    }

    @Test
    void history_ok() throws Exception {
        UUID id = UUID.randomUUID();
        when(sirenCommandService.getHistory(any()))
                .thenReturn(new PageImpl<>(List.of(SirenAction.builder().id(id).action("trigger").build()),
                        PageRequest.of(0, 20), 1));
        mockMvc.perform(get("/api/v1/siren/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("trigger"));
    }

    @Test
    void trigger_withAlertId() throws Exception {
        UUID aid = UUID.randomUUID();
        when(sirenCommandService.triggerSiren(any()))
                .thenReturn(SirenResponse.builder().status("triggered").nodeId("e").build());
        mockMvc.perform(post("/api/v1/siren/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"nodeId\":\"e\",\"sirenUrl\":\"u\",\"alertId\":\"%s\"}", aid)))
                .andExpect(status().isOk());
    }
}
