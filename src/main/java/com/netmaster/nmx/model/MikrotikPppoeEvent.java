package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mikrotik_pppoe_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MikrotikPppoeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private MikrotikDevice device;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "event_type", length = 30)
    private String eventType;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "caller_id", length = 100)
    private String callerId;

    @Column(name = "profile", length = 100)
    private String profile;

    @Column(name = "severity", length = 30)
    private String severity;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "raw_message", columnDefinition = "TEXT")
    private String rawMessage;

    @Column(name = "event_time")
    private LocalDateTime eventTime;

    @Column(name = "fingerprint_hash", length = 128)
    private String fingerprintHash;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "source", length = 20)
    private String source = "api";

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (syncedAt == null) {
            syncedAt = now;
        }
        if (eventTime == null) {
            eventTime = now;
        }
    }
}
