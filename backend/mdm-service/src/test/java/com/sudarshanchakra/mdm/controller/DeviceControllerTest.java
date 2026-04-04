package com.sudarshanchakra.mdm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.mdm.config.JwtAuthFilter;
import com.sudarshanchakra.mdm.config.JwtService;
import com.sudarshanchakra.mdm.model.MdmDevice;
import com.sudarshanchakra.mdm.service.DeviceManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceController.class)
@AutoConfigureMockMvc(addFilters = false)
class DeviceControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    DeviceManagementService deviceService;

    @MockBean
    JwtService jwtService;

    @MockBean
    JwtAuthFilter jwtAuthFilter;

    private MdmDevice sampleDevice() {
        UUID id = UUID.randomUUID();
        return MdmDevice.builder()
                .id(id)
                .farmId(UUID.randomUUID())
                .deviceName("Test Phone")
                .androidId("abc123")
                .model("Samsung A14")
                .osVersion("Android 14")
                .status("active")
                .build();
    }

    @Test
    void listDevices_returnsOk() throws Exception {
        MdmDevice d = sampleDevice();
        when(deviceService.listByFarm()).thenReturn(List.of(d));

        mockMvc.perform(get("/api/v1/mdm/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deviceName").value("Test Phone"));
    }

    @Test
    void getDevice_returnsOk() throws Exception {
        MdmDevice d = sampleDevice();
        when(deviceService.getById(d.getId())).thenReturn(d);

        mockMvc.perform(get("/api/v1/mdm/devices/{id}", d.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.androidId").value("abc123"));
    }

    @Test
    void registerDevice_returnsOk() throws Exception {
        MdmDevice d = sampleDevice();
        when(deviceService.register(any(MdmDevice.class))).thenReturn(d);

        String body = objectMapper.writeValueAsString(d);
        mockMvc.perform(post("/api/v1/mdm/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceName").value("Test Phone"));
    }

    @Test
    void decommission_returnsOk() throws Exception {
        MdmDevice d = sampleDevice();
        d.setStatus("decommissioned");
        when(deviceService.decommission(d.getId())).thenReturn(d);

        mockMvc.perform(patch("/api/v1/mdm/devices/{id}/decommission", d.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("decommissioned"));
    }
}
