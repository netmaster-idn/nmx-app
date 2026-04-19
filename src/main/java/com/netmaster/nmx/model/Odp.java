package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "odps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Odp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "code", length = 20, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "odc_id")
    private Odc odc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_profile_id")
    private CompanyProfile companyProfile;

    @Column(name = "node_type", length = 10)
    private String nodeType = "ODP";

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "splitter", length = 100)
    private String splitter;

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "capacity")
    private Integer capacity = 8;

    @Column(name = "used_port")
    private Integer usedPort = 0;

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
        if (isActive == null) isActive = true;
        if (capacity == null) capacity = 8;
        if (usedPort == null) usedPort = 0;
        if (nodeType == null || nodeType.isBlank()) nodeType = "ODP";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper method to get available port
    public Integer getAvailablePort() {
        return capacity - usedPort;
    }
}

