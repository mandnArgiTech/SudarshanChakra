package com.sudarshanchakra.device.controller.water;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.device.config.SecurityConfig;
import com.sudarshanchakra.device.dto.water.MotorUpdateRequest;
import com.sudarshanchakra.jwt.ResourceServerJwtAuthFilter;
import com.sudarshanchakra.device.model.water.WaterMotorController;
import com.sudarshanchakra.device.service.water.WaterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WaterMotorRestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
@WithMockUser(
        username = "t1",
        authorities = {"PERMISSION_pumps:view", "PERMISSION_pumps:control", "PERMISSION_water:manage"})
class WaterMotorRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WaterService waterService;

    @MockBean
    @SuppressWarnings("unused")
    private ResourceServerJwtAuthFilter resourceServerJwtAuthFilter;

    @Test
    void getAllMotors() throws Exception {
        WaterMotorController m = WaterMotorController.builder()
                .id("m1")
                .farmId(UUID.randomUUID())
                .displayName("Pump")
                .controlType("relay")
                .build();
        when(waterService.getAllMotors()).thenReturn(List.of(m));

        mockMvc.perform(get("/api/v1/water/motors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("m1"));
    }

    @Test
    void getMotor() throws Exception {
        WaterMotorController m = WaterMotorController.builder()
                .id("mid")
                .farmId(UUID.randomUUID())
                .displayName("P")
                .controlType("relay")
                .build();
        when(waterService.getMotor("mid")).thenReturn(m);

        mockMvc.perform(get("/api/v1/water/motors/mid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("mid"));
    }

    @Test
    void sendCommand() throws Exception {
        mockMvc.perform(post("/api/v1/water/motors/m1/command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"command\":\"pump_on\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("published"))
                .andExpect(jsonPath("$.motorId").value("m1"));

        verify(waterService).sendMotorCommand(eq("m1"), eq("pump_on"), eq("manual_app"));
    }

    @Test
    void updateMotor() throws Exception {
        MotorUpdateRequest req = new MotorUpdateRequest();
        req.setAutoMode(false);
        WaterMotorController saved = WaterMotorController.builder()
                .id("m1")
                .farmId(UUID.randomUUID())
                .displayName("P")
                .controlType("relay")
                .autoMode(false)
                .build();
        when(waterService.updateMotor(eq("m1"), org.mockito.ArgumentMatchers.any(MotorUpdateRequest.class)))
                .thenReturn(saved);

        mockMvc.perform(put("/api/v1/water/motors/m1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("autoMode", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("m1"));
    }
}
