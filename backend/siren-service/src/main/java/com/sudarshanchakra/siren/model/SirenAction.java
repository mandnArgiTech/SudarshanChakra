package com.sudarshanchakra.siren.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "siren_actions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SirenAction {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "triggered_by")
    private UUID triggeredBy;

    @Column(name = "triggered_by_system")
    @Builder.Default
    private Boolean triggeredBySystem = false;

    @Column(name = "target_node")
    private String targetNode;

    @Column(nullable = false)
    private String action;

    @Column(name = "alert_id")
    private UUID alertId;

    @Builder.Default
    private Boolean acknowledged = false;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "siren_url")
    private String sirenUrl;

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
