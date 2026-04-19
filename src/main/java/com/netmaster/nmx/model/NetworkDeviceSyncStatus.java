package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "network_device_sync_status",
        uniqueConstraints = @UniqueConstraint(name = "uk_network_device_sync_status_device_module", columnNames = {"device_id", "module_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NetworkDeviceSyncStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private NetworkDevice device;

    @Column(name = "module_name", nullable = false, length = 50)
    private String moduleName;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "fail_count")
    private Integer failCount;

    @Column(name = "stale_after_seconds")
    private Integer staleAfterSeconds;

    @Column(name = "breaker_until")
    private LocalDateTime breakerUntil;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "last_duration_ms")
    private Long lastDurationMs;

    @Column(name = "last_item_count")
    private Integer lastItemCount;

    @PrePersist
    void onCreate() {
        if (failCount == null) {
            failCount = 0;
        }
        if (status == null || status.isBlank()) {
            status = "idle";
        }
    }
}
