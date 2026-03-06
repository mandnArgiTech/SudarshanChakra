package com.sudarshanchakra.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User information response")
public class UserResponse {

    @Schema(description = "User ID")
    private UUID id;

    @Schema(description = "Username")
    private String username;

    @Schema(description = "Email address")
    private String email;

    @Schema(description = "User role")
    private String role;
}
