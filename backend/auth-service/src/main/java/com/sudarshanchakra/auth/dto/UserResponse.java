package com.sudarshanchakra.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
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

    @Schema(description = "Tenant farm id")
    private UUID farmId;

    @Schema(description = "Display name")
    private String displayName;

    @Schema(description = "Account active")
    private Boolean active;

    @Schema(description = "Enabled feature modules for this user")
    private List<String> modules;

    @Schema(description = "Effective permissions")
    private List<String> permissions;
}
