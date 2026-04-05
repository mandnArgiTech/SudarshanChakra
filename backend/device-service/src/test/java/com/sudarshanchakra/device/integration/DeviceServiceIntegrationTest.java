package com.sudarshanchakra.device.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DeviceServiceIntegrationTest extends AbstractDeviceIntegrationTest {

    private static final String FARM_A = "a0000000-0000-0000-0000-000000000001";
    private static final String FARM_B = "b0000000-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withAuth(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) {
        return req.header(HttpHeaders.AUTHORIZATION, DeviceTestJwt.bearerForFarm(UUID.fromString(FARM_A)));
    }

    @Test
    void createNode_list_getById() throws Exception {
        String id = "edge-it-" + System.currentTimeMillis();
        String body = String.format(
                "{\"id\":\"%s\",\"farmId\":\"%s\",\"displayName\":\"IT Node\",\"status\":\"online\"}",
                id, FARM_A);

        mockMvc.perform(withAuth(post("/api/v1/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));

        mockMvc.perform(withAuth(get("/api/v1/nodes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]").exists());

        mockMvc.perform(withAuth(get("/api/v1/nodes/" + id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("IT Node"));
    }

    @Test
    void createCamera_onNode() throws Exception {
        String nodeId = "edge-cam-" + System.currentTimeMillis();
        mockMvc.perform(withAuth(post("/api/v1/nodes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"id\":\"%s\",\"farmId\":\"%s\",\"displayName\":\"N\"}", nodeId, FARM_A))))
                .andExpect(status().isOk());

        String camId = "cam-it-1";
        String camBody = String.format(
                "{\"id\":\"%s\",\"nodeId\":\"%s\",\"name\":\"Gate\",\"rtspUrl\":\"rtsp://x/stream\",\"enabled\":true}",
                camId, nodeId);
        mockMvc.perform(withAuth(post("/api/v1/cameras")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(camBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gate"));

        mockMvc.perform(withAuth(get("/api/v1/cameras").param("nodeId", nodeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(camId));
    }

    @Test
    void waterTanks_listFromSeedData() throws Exception {
        mockMvc.perform(withAuth(get("/api/v1/water/tanks")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='farm_tank1')]").exists())
                .andExpect(jsonPath("$[?(@.displayName=='Farm Tank 1')]").exists());
    }

    @Test
    void waterMotors_listFromSeedData() throws Exception {
        mockMvc.perform(withAuth(get("/api/v1/water/motors")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /** Tenant Hibernate filter: JWT farm B has no edge_nodes in seed data; farm A has two. */
    @Test
    void listNodes_jwtFarmScopesResults() throws Exception {
        mockMvc.perform(get("/api/v1/nodes")
                        .header(HttpHeaders.AUTHORIZATION, DeviceTestJwt.bearerForFarm(UUID.fromString(FARM_B))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(withAuth(get("/api/v1/nodes")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[?(@.id=='edge-node-a')]").exists());
    }
}
