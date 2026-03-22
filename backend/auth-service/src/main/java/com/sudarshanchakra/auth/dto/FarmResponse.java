package com.sudarshanchakra.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Farm (tenant) details")
public class FarmResponse {

    private UUID id;
    private String name;
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
    private OffsetDateTime trialEndsAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
