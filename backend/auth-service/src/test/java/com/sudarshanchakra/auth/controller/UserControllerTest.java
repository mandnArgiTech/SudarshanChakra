package com.sudarshanchakra.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.auth.config.GlobalExceptionHandler;
import com.sudarshanchakra.auth.config.JwtAuthFilter;
import com.sudarshanchakra.auth.config.SecurityConfig;
import com.sudarshanchakra.auth.dto.UserCreateRequest;
import com.sudarshanchakra.auth.dto.UserResponse;
import com.sudarshanchakra.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    @SuppressWarnings("unused")
    JwtAuthFilter jwtAuthFilter;

    @MockBean
    UserService userService;

    @Test
    @WithMockUser(username = "viewer1", roles = "VIEWER")
    void createUser_viewer_returnsForbidden() throws Exception {
        UserCreateRequest body = UserCreateRequest.builder()
                .username("newu")
                .password("secret12")
                .role("viewer")
                .build();
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    void createUser_admin_returnsOk() throws Exception {
        UserCreateRequest body = UserCreateRequest.builder()
                .username("newu")
                .password("secret12")
                .role("viewer")
                .build();
        UUID id = UUID.fromString("b0000000-0000-0000-0000-000000000002");
        when(userService.createUser(eq("admin1"), any(UserCreateRequest.class), nullable(String.class)))
                .thenReturn(UserResponse.builder()
                        .id(id)
                        .username("newu")
                        .role("viewer")
                        .build());
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newu"));
    }
}
