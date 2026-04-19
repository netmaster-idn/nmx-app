package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mikrotik_interface_traffic")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MikrotikInterfaceTraffic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private MikrotikDevice device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interface_id", nullable = false)
    private MikrotikInterface mikrotikInterface;

    @Column(name = "in_octets")
    private Long inOctets;

    @Column(name = "out_octets")
    private Long outOctets;

    @Column(name = "in_bps")
    private Long inBps;

    @Column(name = "out_bps")
    private Long outBps;

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
