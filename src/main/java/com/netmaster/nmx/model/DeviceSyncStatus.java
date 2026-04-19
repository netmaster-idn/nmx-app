package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_sync_status",
        uniqueConstraints = @UniqueConstraint(name = "uk_device_sync_status_device_module", columnNames = {"device_id", "module_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceSyncStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private MikrotikDevice device;

    @Column(name = "module_name", length = 50, nullable = false)
    private String moduleName;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "stale_after_seconds")
    private Integer staleAfterSeconds;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "consecutive_failures")
    private Integer consecutiveFailures;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_duration_ms")
    private Long lastDurationMs;

    @Column(name = "last_item_count")
    private Integer lastItemCount;

    @PrePersist
    void onCreate() {
        if (status == null || status.isBlank()) {
            status = "idle";
        }
        if (consecutiveFailures == null) {
            consecutiveFailures = 0;
        }
    }
}
