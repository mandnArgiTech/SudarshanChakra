package com.sudarshanchakra.siren.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "siren_actions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "farmId", type = UUID.class))
@Filter(
        name = "tenantFilter",
        condition = "target_node in (select en.id from edge_nodes en where en.farm_id = :farmId)")
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
