package com.sudarshanchakra.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
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
public class UserUpdateRequest {

    @Email
    private String email;

    @Size(min = 6, max = 100)
    private String password;

    @Schema(description = "Role value")
    private String role;

    private String displayName;
    private List<String> permissions;
    private List<String> modulesOverride;
    private Boolean active;
}
