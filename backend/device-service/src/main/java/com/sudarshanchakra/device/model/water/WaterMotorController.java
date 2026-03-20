package com.sudarshanchakra.device.model.water;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name = "water_motor_controllers")
public class WaterMotorController {

    @Id @Column(length = 50)
    private String id;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "device_tag", length = 100) private String deviceTag;
    @Column(length = 50)                        private String location;
    @Column(name = "control_type", nullable = false, length = 10) private String controlType; // "relay"|"sms"

    // SMS / Taro Smart Panel config
    @Column(name = "gsm_target_phone", length = 25) private String gsmTargetPhone;
    @Column(name = "gsm_on_message",   length = 100) private String gsmOnMessage;
    @Column(name = "gsm_off_message",  length = 100) private String gsmOffMessage;

    // Auto mode
    @Column(name = "auto_mode")          @Builder.Default private Boolean autoMode = true;
    @Column(name = "pump_on_percent")    @Builder.Default private Double pumpOnPercent = 20.0;
    @Column(name = "pump_off_percent")   @Builder.Default private Double pumpOffPercent = 85.0;
    @Column(name = "max_run_minutes")    @Builder.Default private Integer maxRunMinutes = 30;

    // Runtime state (updated from MQTT)
    @Column(length = 20) @Builder.Default private String state = "stopped";
    @Column(length = 10) @Builder.Default private String mode  = "auto";
    @Column(name = "run_seconds")        @Builder.Default private Integer runSeconds = 0;
    @Column(length = 20)                 @Builder.Default private String status = "unknown";
    @Column(name = "last_seen_at")       private OffsetDateTime lastSeenAt;

    @Column(name = "created_at", updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at")                    private OffsetDateTime updatedAt;

    @PrePersist  protected void onCreate() { createdAt = updatedAt = OffsetDateTime.now(); }
    @PreUpdate   protected void onUpdate() { updatedAt = OffsetDateTime.now(); }
}
