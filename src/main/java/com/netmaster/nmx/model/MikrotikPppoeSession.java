package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mikrotik_pppoe_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MikrotikPppoeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private MikrotikDevice device;

    @Column(name = "username", length = 100, nullable = false)
    private String username;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "caller_id", length = 100)
    private String callerId;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "profile_name", length = 100)
    private String profileName;

    @Column(name = "service", length = 50)
    private String service;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "login_at")
    private LocalDateTime loginAt;

    @Column(name = "logout_at")
    private LocalDateTime logoutAt;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "source", length = 20)
    private String source = "api";
}
