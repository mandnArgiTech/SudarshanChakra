package com.sudarshanchakra.alert.dto;

import com.sudarshanchakra.alert.model.Alert;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AlertResponseTest {

    @Test
    void fromEntity_mapsAllFields() {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        Alert a = Alert.builder()
                .id(id)
                .nodeId("n1")
                .cameraId("c1")
                .zoneId("z1")
                .zoneName("Z")
                .zoneType("hazard")
                .priority("high")
                .detectionClass("person")
                .confidence(0.9f)
                .status("new")
                .workerSuppressed(false)
                .createdAt(now)
                .build();

        AlertResponse r = AlertResponse.fromEntity(a);
        assertThat(r.getId()).isEqualTo(id);
        assertThat(r.getNodeId()).isEqualTo("n1");
        assertThat(r.getPriority()).isEqualTo("high");
        assertThat(r.getDetectionClass()).isEqualTo("person");
        assertThat(r.getConfidence()).isEqualTo(0.9f);
        assertThat(r.getStatus()).isEqualTo("new");
        assertThat(r.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void fromEntity_handlesNullOptionalFields() {
        Alert a = Alert.builder()
                .id(UUID.randomUUID())
                .nodeId("n")
                .zoneId("z")
                .zoneName("Z")
                .zoneType("intrusion")
                .priority("warning")
                .detectionClass("cow")
                .confidence(0.5f)
                .status("new")
                .workerSuppressed(null)
                .createdAt(OffsetDateTime.now())
                .build();

        AlertResponse r = AlertResponse.fromEntity(a);
        assertThat(r.getCameraId()).isNull();
        assertThat(r.getWorkerSuppressed()).isNull();
    }

    @Test
    void builder_roundTrip() {
        UUID id = UUID.randomUUID();
        AlertResponse r = AlertResponse.builder()
                .id(id)
                .nodeId("x")
                .priority("critical")
                .detectionClass("fire")
                .confidence(1f)
                .status("new")
                .build();
        assertThat(r.getId()).isEqualTo(id);
        assertThat(r.getPriority()).isEqualTo("critical");
    }
}
