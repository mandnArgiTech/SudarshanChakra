package com.sudarshanchakra.mdm.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mdm_app_usage")
public class AppUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "package_name", nullable = false, length = 200)
    private String packageName;

    @Column(name = "app_label", length = 200)
    private String appLabel;

    @Column(name = "foreground_time_sec", nullable = false)
    @Builder.Default
    private Integer foregroundTimeSec = 0;

    @Column(name = "launch_count")
    @Builder.Default
    private Integer launchCount = 0;

    @Column(length = 50)
    private String category;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
