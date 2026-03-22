package com.sudarshanchakra.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudarshanchakra.auth.dto.AuthResponse;
import com.sudarshanchakra.auth.dto.LoginRequest;
import com.sudarshanchakra.auth.dto.RegisterRequest;
import com.sudarshanchakra.auth.dto.UserResponse;
import com.sudarshanchakra.auth.config.GlobalExceptionHandler;
import com.sudarshanchakra.auth.service.AuthService;
import com.sudarshanchakra.auth.repository.UserRepository;
import com.sudarshanchakra.auth.service.JwtService;
import com.sudarshanchakra.auth.service.ModuleResolutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    AuthService authService;

    @MockBean
    JwtService jwtService;

    @MockBean
    UserRepository userRepository;

    @MockBean
    ModuleResolutionService moduleResolutionService;

    @Test
    void login_ok() throws Exception {
        when(authService.login(any(LoginRequest.class), any()))
                .thenReturn(AuthResponse.builder()
                        .token("jwt")
                        .refreshToken("ref")
                        .user(UserResponse.builder()
                                .id(UUID.randomUUID())
                                .username("u")
                                .role("admin")
                                .build())
                        .build());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"u\",\"password\":\"p\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt"));
    }

    @Test
    void register_ok() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(AuthResponse.builder().token("t").refreshToken("r")
                        .user(UserResponse.builder().id(UUID.randomUUID()).username("newuser").role("viewer").build())
                        .build());
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newuser\",\"password\":\"secret1\",\"email\":\"e@e.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("newuser"));
    }

    @Test
    void login_validationFails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_validationFails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ab\",\"password\":\"1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_serviceError_returnsBadRequest() throws Exception {
        when(authService.login(any(), any())).thenThrow(new IllegalArgumentException("bad creds"));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x\",\"password\":\"y\"}"))
                .andExpect(status().isBadRequest());
    }
}
