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
@Table(name = "mdm_screen_time")
public class ScreenTime {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "total_screen_time_sec")
    @Builder.Default
    private Integer totalScreenTimeSec = 0;

    @Column(name = "unlock_count")
    @Builder.Default
    private Integer unlockCount = 0;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
