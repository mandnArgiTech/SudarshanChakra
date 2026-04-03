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
@Table(name = "mdm_ota_packages")
public class OtaPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(name = "apk_url", nullable = false, columnDefinition = "TEXT")
    private String apkUrl;

    @Column(name = "apk_sha256", nullable = false, length = 64)
    private String apkSha256;

    @Column(name = "apk_size_bytes")
    private Long apkSizeBytes;

    @Column(name = "release_notes", columnDefinition = "TEXT")
    private String releaseNotes;

    @Column
    @Builder.Default
    private Boolean mandatory = false;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
