package com.sudarshanchakra.device.controller;

import com.sudarshanchakra.device.config.SecurityConfig;
import com.sudarshanchakra.device.model.WorkerTag;
import com.sudarshanchakra.device.service.DeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkerTagController.class)
@Import(SecurityConfig.class)
class WorkerTagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceService deviceService;

    @Test
    void getAllTags() throws Exception {
        when(deviceService.getAllTags()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/tags")).andExpect(status().isOk());
    }

    @Test
    void getTagsByFarmId() throws Exception {
        UUID farm = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        WorkerTag t = new WorkerTag();
        t.setTagId("TAG-1");
        when(deviceService.getTagsByFarmId(farm)).thenReturn(List.of(t));

        mockMvc.perform(get("/api/v1/tags").param("farmId", farm.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tagId").value("TAG-1"));
    }

    @Test
    void getTagById() throws Exception {
        WorkerTag t = new WorkerTag();
        t.setTagId("T1");
        when(deviceService.getTagById("T1")).thenReturn(t);

        mockMvc.perform(get("/api/v1/tags/T1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagId").value("T1"));
    }
}
