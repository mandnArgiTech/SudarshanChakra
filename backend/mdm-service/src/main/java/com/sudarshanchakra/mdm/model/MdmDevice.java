package com.sudarshanchakra.mdm.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mdm_devices")
public class MdmDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "device_name", nullable = false, length = 200)
    private String deviceName;

    @Column(name = "android_id", nullable = false, unique = true, length = 64)
    private String androidId;

    @Column(length = 100)
    private String model;

    @Column(name = "os_version", length = 20)
    private String osVersion;

    @Column(name = "app_version", length = 20)
    private String appVersion;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(length = 20)
    private String imei;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "is_device_owner")
    @Builder.Default
    private Boolean isDeviceOwner = false;

    @Column(name = "is_lock_task_active")
    @Builder.Default
    private Boolean isLockTaskActive = false;

    @Column(name = "kiosk_pin_hash")
    private String kioskPinHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "whitelisted_apps", columnDefinition = "jsonb")
    private String whitelistedApps;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String policies;

    @Column(name = "last_heartbeat")
    private Instant lastHeartbeat;

    @Column(name = "last_telemetry_sync")
    private Instant lastTelemetrySync;

    @Column(name = "last_latitude")
    private Double lastLatitude;

    @Column(name = "last_longitude")
    private Double lastLongitude;

    @Column(name = "last_location_at")
    private Instant lastLocationAt;

    @Column(name = "location_interval_sec")
    @Builder.Default
    private Integer locationIntervalSec = 60;

    @Column(name = "mqtt_client_id", length = 100)
    private String mqttClientId;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "pending";

    @Column(name = "provisioned_at")
    private Instant provisionedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
