package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "devices_status_cache",
        uniqueConstraints = @UniqueConstraint(name = "uk_devices_status_cache_device", columnNames = "device_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceStatusCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false, unique = true)
    private NetworkDevice device;

    @Column(name = "device_name", length = 100, nullable = false)
    private String deviceName;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "role_name", length = 50)
    private String role;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "maintenance_status", length = 20)
    private String maintenanceStatus;

    @Column(name = "warning_status", length = 20)
    private String warningStatus;

    @Column(name = "cpu_usage")
    private BigDecimal cpuUsage;

    @Column(name = "memory_usage")
    private BigDecimal memoryUsage;

    @Column(name = "uptime_seconds")
    private Long uptime;

    @Column(name = "latency_ms")
    private BigDecimal latencyMs;

    @Column(name = "packet_loss")
    private BigDecimal packetLoss;

    @Column(name = "health_score")
    private Integer healthScore;

    @Column(name = "freshness_status", length = 20)
    private String freshnessStatus;

    @Column(name = "alert_count")
    private Integer alertCount;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(name = "last_ping_success_at")
    private LocalDateTime lastPingSuccessAt;

    @Column(name = "last_api_success_at")
    private LocalDateTime lastApiSuccessAt;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "sync_source", length = 30)
    private String syncSource;

    @Column(name = "maintenance_reason", length = 500)
    private String maintenanceReason;

    @Column(name = "maintenance_starts_at")
    private LocalDateTime maintenanceStartsAt;

    @Column(name = "maintenance_ends_at")
    private LocalDateTime maintenanceEndsAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
