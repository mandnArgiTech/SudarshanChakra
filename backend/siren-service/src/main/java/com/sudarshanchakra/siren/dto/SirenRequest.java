package com.sudarshanchakra.siren.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SirenRequest {

    private String nodeId;
    private String sirenUrl;
    private UUID alertId;
}
