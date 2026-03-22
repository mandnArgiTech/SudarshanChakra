package com.sudarshanchakra.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User registration request")
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters")
    @Schema(description = "Desired username", example = "newuser")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be between 6 and 100 characters")
    @Schema(description = "Password", example = "securePass123")
    private String password;

    @Email(message = "Email must be valid")
    @Schema(description = "Email address", example = "user@farm.com")
    private String email;

    @Schema(
            description = "User role (super_admin not allowed via public register)",
            example = "viewer",
            allowableValues = {"admin", "manager", "operator", "viewer"})
    private String role;

    @Schema(description = "Farm ID (UUID)", example = "a0000000-0000-0000-0000-000000000001")
    private String farmId;
}
