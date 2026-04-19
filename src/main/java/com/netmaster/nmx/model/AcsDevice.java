package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "acs_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AcsDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "acs_device_id", length = 255)
    private String acsDeviceId;

    @Column(name = "ip_address", length = 15)
    private String ipAddress;

    @Column(name = "mac_address", length = 17)
    private String macAddress;

    @Column(name = "device_type", length = 50)
    private String deviceType; // ONT, Router, Switch, etc.

    @Column(name = "vendor", length = 50)
    private String vendor; // Huawei, ZTE, FiberHome, etc.

    @Column(name = "model", length = 50)
    private String model;

    @Column(name = "serial_number", length = 50)
    private String serialNumber;

    @Column(name = "firmware_version", length = 50)
    private String firmwareVersion;

    @Column(name = "software_version", length = 50)
    private String softwareVersion;

    @Column(name = "olt_id")
    private Long oltId;

    @Column(name = "olt_port")
    private Integer oltPort;

    @Column(name = "onu_id")
    private Integer onuId;

    @Column(name = "optical_rx_power")
    private Double opticalRxPower;

    @Column(name = "optical_tx_power")
    private Double opticalTxPower;

    @Column(name = "optical_temperature")
    private Double opticalTemperature;

    @Column(name = "optical_voltage")
    private Double opticalVoltage;

    @Column(name = "wifi_name", length = 100)
    private String wifiName;

    @Column(name = "status", length = 20)
    private String status = "offline"; // online, offline, dying_gasp

    @Column(name = "connection_type", length = 20)
    private String connectionType; // GPON, EPON, GEPON

    @Column(name = "distance")
    private Integer distance; // in meters

    @Column(name = "last_acs_request")
    private LocalDateTime lastAcsRequest;

    @Column(name = "last_inform")
    private LocalDateTime lastInform;

    @Column(name = "is_provisioned")
    private boolean isProvisioned = false;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

