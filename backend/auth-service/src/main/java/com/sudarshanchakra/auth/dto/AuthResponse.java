package com.sudarshanchakra.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication response with JWT tokens")
public class AuthResponse {

    @Schema(description = "JWT access token")
    private String token;

    @Schema(description = "JWT refresh token")
    private String refreshToken;

    @Schema(description = "User information")
    private UserResponse user;
}
