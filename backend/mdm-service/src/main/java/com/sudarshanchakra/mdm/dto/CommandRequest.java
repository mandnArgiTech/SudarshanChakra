package com.sudarshanchakra.mdm.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CommandRequest(
        @NotNull UUID deviceId,
        @NotBlank String command,
        JsonNode payload
) {
}
