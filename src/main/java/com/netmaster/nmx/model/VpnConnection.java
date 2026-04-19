package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "vpn_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VpnConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "vpn_type", length = 20)
    private String vpnType; // PPTP, L2TP, SSTP, OpenVPN, IPSec

    @Column(name = "remote_ip", length = 15)
    private String remoteIp;

    @Column(name = "local_ip", length = 15)
    private String localIp;

    @Column(name = "remote_network", length = 50)
    private String remoteNetwork;

    @Column(name = "local_network", length = 50)
    private String localNetwork;

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "password", length = 100)
    private String password;

    @Column(name = "secret", length = 100)
    private String secret;

    @Column(name = "status", length = 20)
    private String status = "disconnected"; // connected, disconnected, error

    @Column(name = "last_connected")
    private LocalDateTime lastConnected;

    @Column(name = "bandwidth_limit")
    private Long bandwidthLimit; // in Kbps

    @Column(name = "description", length = 500)
    private String description;

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

