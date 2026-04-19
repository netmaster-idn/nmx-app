package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "package_id")
    private InternetPackage internetPackage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_type_id")
    private ServiceType serviceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "odp_id")
    private Odp odp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_id")
    private Technician technician;

    @Column(name = "odp_port")
    private Integer odpPort;

    @Column(name = "ont_serial", length = 50)
    private String ontSerial;

    @Column(name = "ont_brand", length = 50)
    private String ontBrand;

    @Column(name = "ont_redaman", precision = 6, scale = 2)
    private BigDecimal ontRedaman;

    @Column(name = "wifi_name", length = 100)
    private String wifiName;

    @Column(name = "wifi_password", length = 100)
    private String wifiPassword;

    @Column(name = "ont_standby_since")
    private LocalDateTime ontStandbySince;

    @Column(name = "last_restart_requested_at")
    private LocalDateTime lastRestartRequestedAt;

    @Column(name = "pppoe_username", length = 50, unique = true)
    private String pppoeUsername;

    @Column(name = "pppoe_password", length = 100)
    private String pppoePassword;

    @Column(name = "ip_address", length = 15)
    private String ipAddress;

    @Column(name = "mac_address", length = 17)
    private String macAddress;

    @Column(name = "monthly_fee", precision = 12, scale = 2, nullable = false)
    private BigDecimal monthlyFee;

    @Column(name = "installation_fee", precision = 12, scale = 2)
    private BigDecimal installationFee;

    @Column(name = "installation_date")
    private LocalDate installationDate;

    @Column(name = "activation_date")
    private LocalDate activationDate;

    @Column(name = "status", length = 20)
    private String status = "pending";

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = "pending";
        if (monthlyFee == null) monthlyFee = BigDecimal.ZERO;
        if (installationFee == null) installationFee = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

