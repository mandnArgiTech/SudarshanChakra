package com.sudarshanchakra.device.controller;

import com.sudarshanchakra.device.config.SecurityConfig;
import com.sudarshanchakra.device.model.WorkerTag;
import com.sudarshanchakra.device.service.DeviceService;
import com.sudarshanchakra.jwt.ResourceServerJwtAuthFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkerTagController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
@WithMockUser(
        username = "t1",
        authorities = {"PERMISSION_devices:view", "PERMISSION_devices:manage"})
class WorkerTagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceService deviceService;

    @MockBean
    @SuppressWarnings("unused")
    private ResourceServerJwtAuthFilter resourceServerJwtAuthFilter;

    @Test
    void getAllTags() throws Exception {
        when(deviceService.getAllTags()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/tags")).andExpect(status().isOk());
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
