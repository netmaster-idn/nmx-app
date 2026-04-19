package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServiceType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "service_type", length = 50)
    private String serviceType;

    @Column(name = "authentication_method", length = 20)
    private String authenticationMethod = "CHAP";

    @Column(name = "support_pppoe")
    private Boolean supportPppoe = false;

    @Column(name = "support_hotspot")
    private Boolean supportHotspot = false;

    @Column(name = "support_static_ip")
    private Boolean supportStaticIp = false;

    @Column(name = "support_dhcp")
    private Boolean supportDhcp = false;

    @Column(name = "support_olt")
    private Boolean supportOlt = false;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

