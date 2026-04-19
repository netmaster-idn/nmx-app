package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "mikrotik_interfaces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MikrotikInterface {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private MikrotikDevice device;

    @Column(name = "interface_name", length = 100, nullable = false)
    private String interfaceName;

    @Column(name = "interface_index", nullable = false)
    private Integer interfaceIndex;

    @Column(name = "interface_type", length = 50)
    private String interfaceType;

    @Column(name = "is_monitored")
    private Boolean monitored = true;

    @Column(name = "priority")
    private Integer priority = 100;

    @Column(name = "comment", length = 255)
    private String comment;

    @Column(name = "admin_status", length = 20)
    private String adminStatus;

    @Column(name = "oper_status", length = 20)
    private String operStatus;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
        if (monitored == null) {
            monitored = true;
        }
        if (priority == null) {
            priority = 100;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
