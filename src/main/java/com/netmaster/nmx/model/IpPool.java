package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ip_pools")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IpPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "pool_name", length = 50, unique = true)
    private String poolName;

    @Column(name = "start_ip", length = 15, nullable = false)
    private String startIp;

    @Column(name = "end_ip", length = 15, nullable = false)
    private String endIp;

    @Column(name = "gateway", length = 15)
    private String gateway;

    @Column(name = "dns_primary", length = 15)
    private String dnsPrimary;

    @Column(name = "dns_secondary", length = 15)
    private String dnsSecondary;

    @Column(name = "vlan")
    private Integer vlan;

    @Column(name = "network_address", length = 15)
    private String networkAddress;

    @Column(name = "cidr_prefix")
    private Integer cidrPrefix;

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

