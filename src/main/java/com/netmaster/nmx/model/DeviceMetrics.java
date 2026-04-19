package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Device Metrics - Time-series monitoring data
 */
@Entity
@Table(name = "device_metrics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private NetworkDevice device;

    // Resource metrics
    @Column(name = "cpu_usage")
    private BigDecimal cpuUsage;

    @Column(name = "memory_usage")
    private BigDecimal memoryUsage;

    @Column(name = "memory_used")
    private Long memoryUsed;

    @Column(name = "memory_total")
    private Long memoryTotal;

    @Column(name = "disk_usage")
    private BigDecimal diskUsage;

    @Column(name = "disk_used")
    private Long diskUsed;

    @Column(name = "disk_total")
    private Long diskTotal;

    @Column(name = "temperature")
    private BigDecimal temperature;

    // Network metrics
    @Column(name = "traffic_rx_bytes")
    private Long trafficRxBytes;

    @Column(name = "traffic_tx_bytes")
    private Long trafficTxBytes;

    @Column(name = "traffic_rx_bps")
    private Long trafficRxBps;

    @Column(name = "traffic_tx_bps")
    private Long trafficTxBps;

    @Column(name = "packets_rx")
    private Long packetsRx;

    @Column(name = "packets_tx")
    private Long packetsTx;

    @Column(name = "errors_rx")
    private Long errorsRx;

    @Column(name = "errors_tx")
    private Long errorsTx;

    // Interface specific
    @Column(name = "interface_name", length = 50)
    private String interfaceName;

    @Column(name = "interface_status", length = 20)
    private String interfaceStatus;

    @Column(name = "interface_speed")
    private Integer interfaceSpeed;

    @Column(name = "interface_duplex", length = 20)
    private String interfaceDuplex;

    // OLT specific metrics
    @Column(name = "onu_total")
    private Integer onuTotal;

    @Column(name = "onu_online")
    private Integer onuOnline;

    @Column(name = "onu_offline")
    private Integer onuOffline;

    @Column(name = "optical_rx_power")
    private BigDecimal opticalRxPower;

    @Column(name = "optical_tx_power")
    private BigDecimal opticalTxPower;

    @Column(name = "pon_port_status", length = 20)
    private String ponPortStatus;

    // Router specific
    @Column(name = "active_sessions")
    private Integer activeSessions;

    @Column(name = "active_pppoe")
    private Integer activePppoe;

    @Column(name = "active_hotspot")
    private Integer activeHotspot;

    @Column(name = "firewall_connections")
    private Integer firewallConnections;

    // Latency metrics
    @Column(name = "latency_ms")
    private BigDecimal latencyMs;

    @Column(name = "jitter_ms")
    private BigDecimal jitterMs;

    @Column(name = "packet_loss")
    private BigDecimal packetLoss;

    // Uptime in seconds
    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    // Helper methods
    public BigDecimal getMemoryUsagePercent() {
        if (memoryTotal != null && memoryTotal > 0 && memoryUsed != null) {
            return BigDecimal.valueOf(memoryUsed * 100.0 / memoryTotal)
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    public String getTrafficRxFormatted() {
        return formatBytes(trafficRxBytes);
    }

    public String getTrafficTxFormatted() {
        return formatBytes(trafficTxBytes);
    }

    public String getTrafficRxBpsFormatted() {
        return formatBps(trafficRxBps);
    }

    public String getTrafficTxBpsFormatted() {
        return formatBps(trafficTxBps);
    }

    private String formatBytes(Long bytes) {
        if (bytes == null) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private String formatBps(Long bps) {
        if (bps == null) return "0 bps";
        if (bps < 1000) return bps + " bps";
        if (bps < 1000000) return String.format("%.1f Kbps", bps / 1000.0);
        if (bps < 1000000000) return String.format("%.1f Mbps", bps / 1000000.0);
        return String.format("%.2f Gbps", bps / 1000000000.0);
    }

    public boolean isCritical() {
        if (cpuUsage != null && cpuUsage.intValue() >= 90) return true;
        if (memoryUsage != null && memoryUsage.intValue() >= 95) return true;
        if (temperature != null && temperature.intValue() >= 60) return true;
        if (packetLoss != null && packetLoss.intValue() >= 5) return true;
        return false;
    }

    public boolean isWarning() {
        if (cpuUsage != null && cpuUsage.intValue() >= 70) return true;
        if (memoryUsage != null && memoryUsage.intValue() >= 80) return true;
        if (temperature != null && temperature.intValue() >= 45) return true;
        if (packetLoss != null && packetLoss.intValue() >= 1) return true;
        return false;
    }
}

