package com.sudarshanchakra.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "FCM token update request")
public class FcmTokenRequest {

    @NotBlank(message = "FCM token is required")
    @Schema(description = "Firebase Cloud Messaging token")
    private String fcmToken;
}
