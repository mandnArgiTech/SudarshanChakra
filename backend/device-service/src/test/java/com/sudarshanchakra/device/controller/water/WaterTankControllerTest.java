package com.sudarshanchakra.device.controller.water;

import com.sudarshanchakra.device.config.SecurityConfig;
import com.sudarshanchakra.device.dto.water.WaterTankResponse;
import com.sudarshanchakra.device.model.water.WaterLevelReading;
import com.sudarshanchakra.device.service.water.WaterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WaterTankController.class)
@Import(SecurityConfig.class)
class WaterTankControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WaterService waterService;

    @Test
    void getAllTanks() throws Exception {
        WaterTankResponse r = WaterTankResponse.builder().id("t1").displayName("Main").build();
        when(waterService.getAllTanks()).thenReturn(List.of(r));

        mockMvc.perform(get("/api/v1/water/tanks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("t1"))
                .andExpect(jsonPath("$[0].displayName").value("Main"));
    }

    @Test
    void getTank() throws Exception {
        WaterTankResponse r = WaterTankResponse.builder().id("x").displayName("X").build();
        when(waterService.getTank("x")).thenReturn(r);

        mockMvc.perform(get("/api/v1/water/tanks/x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("x"));
    }

    @Test
    void getHistory() throws Exception {
        WaterLevelReading reading = WaterLevelReading.builder()
                .tankId("t1")
                .percentFilled(50.0)
                .build();
        when(waterService.getHistory("t1", 12)).thenReturn(List.of(reading));

        mockMvc.perform(get("/api/v1/water/tanks/t1/history").param("hours", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].percentFilled").value(50.0));

        verify(waterService).getHistory("t1", 12);
    }
}
