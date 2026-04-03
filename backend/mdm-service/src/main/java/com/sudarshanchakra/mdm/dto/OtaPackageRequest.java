package com.sudarshanchakra.mdm.dto;

import jakarta.validation.constraints.NotBlank;

public record OtaPackageRequest(
        @NotBlank String version,
        @NotBlank String apkUrl,
        @NotBlank String apkSha256,
        Long apkSizeBytes,
        String releaseNotes,
        boolean mandatory
) {
}
