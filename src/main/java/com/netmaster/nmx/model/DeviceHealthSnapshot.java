package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_health_snapshots",
        indexes = {
                @Index(name = "idx_device_health_snapshots_device_time", columnList = "device_id, captured_at DESC"),
                @Index(name = "idx_device_health_snapshots_captured_at", columnList = "captured_at DESC")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceHealthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private NetworkDevice device;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "cpu_usage")
    private BigDecimal cpuUsage;

    @Column(name = "memory_usage")
    private BigDecimal memoryUsage;

    @Column(name = "latency_ms")
    private BigDecimal latencyMs;

    @Column(name = "packet_loss")
    private BigDecimal packetLoss;

    @Column(name = "health_score")
    private Integer healthScore;

    @Column(name = "freshness_status", length = 20)
    private String freshnessStatus;

    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @PrePersist
    void onCreate() {
        if (capturedAt == null) {
            capturedAt = LocalDateTime.now();
        }
    }
}
