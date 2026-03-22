package com.sudarshanchakra.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Create or update farm (super admin)")
public class FarmRequest {

    @NotBlank(groups = Create.class)
    @Size(max = 200)
    private String name;

    @NotBlank(groups = Create.class)
    @Size(max = 50)
    private String slug;

    private String ownerName;
    private String contactPhone;
    private String contactEmail;
    private String status;
    private String subscriptionPlan;
    private List<String> modulesEnabled;
    private Integer maxCameras;
    private Integer maxNodes;
    private Integer maxUsers;

    @Schema(description = "Optional initial farm admin (create only)")
    private String initialAdminUsername;

    @Schema(description = "Initial admin password (create only)")
    private String initialAdminPassword;

    private String initialAdminEmail;
    private String initialAdminDisplayName;

    public interface Create {
    }
}
