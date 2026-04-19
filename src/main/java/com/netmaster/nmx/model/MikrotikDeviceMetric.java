package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "mikrotik_device_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MikrotikDeviceMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private MikrotikDevice device;

    @Column(name = "cpu_load")
    private Integer cpuLoad;

    @Column(name = "memory_total")
    private Long memoryTotal;

    @Column(name = "memory_used")
    private Long memoryUsed;

    @Column(name = "memory_free")
    private Long memoryFree;

    @Column(name = "uptime")
    private Long uptime;

    @Column(name = "temperature")
    private BigDecimal temperature;

    @Column(name = "voltage")
    private BigDecimal voltage;

    @Column(name = "board_health", length = 100)
    private String boardHealth;

    @Column(name = "source", length = 20)
    private String source = "api";

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    @PrePersist
    void onCreate() {
        if (collectedAt == null) {
            collectedAt = LocalDateTime.now();
        }
        if (source == null || source.isBlank()) {
            source = "api";
        }
    }
}
