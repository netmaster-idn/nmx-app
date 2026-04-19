package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Unified Network Device model for monitoring
 * Supports: Core Router, Mikrotik Router, OLT, Switch, Access Point, Server
 */
@Entity
@Table(name = "network_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NetworkDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Column(name = "device_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private DeviceType deviceType;

    @Column(name = "ip_address", nullable = false, length = 45)
    private String ipAddress;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "vendor", length = 50)
    private String vendor;

    @Column(name = "model", length = 50)
    private String model;

    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "mac_address", length = 17)
    private String macAddress;

    @Column(name = "status", length = 20)
    @Enumerated(EnumType.STRING)
    private DeviceStatus status;

    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    @Column(name = "last_ping_time")
    private LocalDateTime lastPingTime;

    @Column(name = "last_snmp_time")
    private LocalDateTime lastSnmpTime;

    @Column(name = "ping_interval")
    private Integer pingInterval = 30;

    @Column(name = "snmp_interval")
    private Integer snmpInterval = 60;

    @Column(name = "snmp_community", length = 100)
    private String snmpCommunity;

    @Column(name = "snmp_version", length = 10)
    private String snmpVersion;

    @Column(name = "api_port")
    private Integer apiPort;

    @Column(name = "api_username", length = 100)
    private String apiUsername;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    // Thresholds
    @Column(name = "cpu_warning_threshold")
    private Integer cpuWarningThreshold = 70;

    @Column(name = "cpu_critical_threshold")
    private Integer cpuCriticalThreshold = 90;

    @Column(name = "memory_warning_threshold")
    private Integer memoryWarningThreshold = 80;

    @Column(name = "memory_critical_threshold")
    private Integer memoryCriticalThreshold = 95;

    @Column(name = "temperature_warning_threshold")
    private Integer temperatureWarningThreshold = 45;

    @Column(name = "temperature_critical_threshold")
    private Integer temperatureCriticalThreshold = 60;

    // Parent device for topology
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_device_id")
    private NetworkDevice parentDevice;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_monitored")
    private Boolean isMonitored = true;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = DeviceStatus.OFFLINE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Device type enumeration
    public enum DeviceType {
        CORE_ROUTER,
        MIKROTIK,
        OLT,
        SWITCH,
        AP,
        SERVER,
        FIREWALL,
        LOAD_BALANCER,
        IDS_IPS,
        UNKNOWN
    }

    // Device status enumeration
    public enum DeviceStatus {
        ONLINE,
        OFFLINE,
        WARNING,
        MAINTENANCE,
        UNKNOWN
    }

    // Helper methods
    public String getUptimeFormatted() {
        if (uptimeSeconds == null || uptimeSeconds == 0) {
            return "-";
        }
        long days = uptimeSeconds / 86400;
        long hours = (uptimeSeconds % 86400) / 3600;
        long minutes = (uptimeSeconds % 3600) / 60;
        
        if (days > 0) {
            return String.format("%dd %dh", days, hours);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    public boolean isOnline() {
        return status == DeviceStatus.ONLINE;
    }

    public boolean needsAttention() {
        return status == DeviceStatus.WARNING || status == DeviceStatus.OFFLINE;
    }
}

