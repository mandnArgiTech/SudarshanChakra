package com.sudarshanchakra.mdm.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mdm_call_logs")
public class CallLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "phone_number_masked", length = 20)
    private String phoneNumberMasked;

    @Column(name = "call_type", nullable = false, length = 20)
    private String callType;

    @Column(name = "call_timestamp", nullable = false)
    private Instant callTimestamp;

    @Column(name = "duration_sec")
    @Builder.Default
    private Integer durationSec = 0;

    @Column(name = "contact_name", length = 200)
    private String contactName;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
