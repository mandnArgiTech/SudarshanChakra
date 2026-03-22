package com.sudarshanchakra.device.controller;

import com.sudarshanchakra.device.config.SecurityConfig;
import com.sudarshanchakra.device.model.Zone;
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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ZoneController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SecurityConfig.class)
@WithMockUser(
        username = "t1",
        authorities = {"PERMISSION_zones:view", "PERMISSION_zones:manage"})
class ZoneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceService deviceService;

    @MockBean
    @SuppressWarnings("unused")
    private ResourceServerJwtAuthFilter resourceServerJwtAuthFilter;

    @Test
    void getAllZones() throws Exception {
        when(deviceService.getAllZones()).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/zones")).andExpect(status().isOk());
    }

    @Test
    void getZonesByCameraId() throws Exception {
        Zone z = new Zone();
        z.setId("z1");
        z.setName("Fence");
        when(deviceService.getZonesByCameraId("cam-1")).thenReturn(List.of(z));

        mockMvc.perform(get("/api/v1/zones").param("cameraId", "cam-1"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteZone() throws Exception {
        mockMvc.perform(delete("/api/v1/zones/z1")).andExpect(status().isNoContent());
        verify(deviceService).deleteZone("z1");
    }
}
