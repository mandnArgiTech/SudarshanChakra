package com.sudarshanchakra.device.controller;

import com.sudarshanchakra.device.config.SecurityConfig;
import com.sudarshanchakra.device.model.Camera;
import com.sudarshanchakra.device.service.DeviceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CameraController.class)
@Import(SecurityConfig.class)
class CameraControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceService deviceService;

    @Test
    void getCamerasByNodeId() throws Exception {
        Camera c = new Camera();
        c.setId("cam-1");
        c.setName("Gate");
        c.setNodeId("node-a");
        when(deviceService.getCamerasByNodeId("node-a")).thenReturn(List.of(c));

        mockMvc.perform(get("/api/v1/cameras").param("nodeId", "node-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("cam-1"));
    }

    @Test
    void getCameraById() throws Exception {
        Camera c = new Camera();
        c.setId("x");
        c.setName("N");
        when(deviceService.getCameraById("x")).thenReturn(c);

        mockMvc.perform(get("/api/v1/cameras/x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("x"));
    }
}
