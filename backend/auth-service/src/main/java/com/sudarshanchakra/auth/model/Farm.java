package com.sudarshanchakra.auth.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "farms")
public class Farm {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 50)
    private String slug;

    @Column(name = "owner_name", length = 200)
    private String ownerName;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "location_lat")
    private Double locationLat;

    @Column(name = "location_lng")
    private Double locationLng;

    @Column(length = 50)
    @Builder.Default
    private String timezone = "Asia/Kolkata";

    @Column(length = 20)
    @Builder.Default
    private String status = "active";

    @Column(name = "subscription_plan", length = 50)
    @Builder.Default
    private String subscriptionPlan = "full";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "modules_enabled", columnDefinition = "jsonb")
    private List<String> modulesEnabled;

    @Column(name = "max_cameras")
    @Builder.Default
    private Integer maxCameras = 8;

    @Column(name = "max_nodes")
    @Builder.Default
    private Integer maxNodes = 2;

    @Column(name = "max_users")
    @Builder.Default
    private Integer maxUsers = 10;

    @Column(name = "trial_ends_at")
    private OffsetDateTime trialEndsAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
