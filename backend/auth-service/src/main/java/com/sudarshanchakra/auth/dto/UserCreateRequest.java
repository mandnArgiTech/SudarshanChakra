package com.sudarshanchakra.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequest {

    @NotBlank
    @Size(min = 3, max = 100)
    private String username;

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    @Email
    private String email;

    @NotBlank
    @Schema(description = "Role value", example = "viewer")
    private String role;

    @Schema(description = "Target farm UUID (super_admin only; others use own farm)")
    private String farmId;

    private String displayName;
    private List<String> permissions;
    private List<String> modulesOverride;
}
