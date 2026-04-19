package com.netmaster.nmx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "olt_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OltDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "vpn_ip_address", length = 45)
    private String vpnIpAddress;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "password", length = 100)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(name = "vendor", length = 50)
    private String vendor; // Huawei, ZTE, FiberHome, etc.

    @Column(name = "model", length = 50)
    private String model;

    @Column(name = "serial_number", length = 50)
    private String serialNumber;

    @Column(name = "slot_count")
    private Integer slotCount;

    @Column(name = "pon_port_count")
    private Integer ponPortCount;

    @Column(name = "gpon_port_count")
    private Integer gponPortCount;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "status", length = 20)
    private String status = "offline"; // online, offline, maintenance

    @Column(name = "onu_count")
    private Integer onuCount;

    @Column(name = "optical_rx_power")
    private Double opticalRxPower;

    @Column(name = "optical_tx_power")
    private Double opticalTxPower;

    @Column(name = "temperature")
    private Double temperature;

    @Column(name = "voltage")
    private Double voltage;

    @Column(name = "last_monitored")
    private LocalDateTime lastMonitored;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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

