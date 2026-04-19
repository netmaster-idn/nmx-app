package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
@SQLDelete(sql = "UPDATE customers SET is_deleted = true, status = 'terminated', updated_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("COALESCE(is_deleted, false) = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_code", length = 20, unique = true)
    private String customerCode;

    @Column(name = "full_name", length = 100, nullable = false)
    private String fullName;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "phone", length = 20, nullable = false)
    private String phone;

    @Column(name = "whatsapp_number", length = 30)
    private String whatsappNumber;

    @Column(name = "ktp_number", length = 20, unique = true)
    private String ktpNumber;

    @Column(name = "ktp_address", columnDefinition = "TEXT")
    private String ktpAddress;

    @Column(name = "installation_address", columnDefinition = "TEXT", nullable = false)
    private String installationAddress;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    @Column(name = "latitude", precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "status", length = 20)
    private String status = "pending";

    @Column(name = "billing_due_day")
    private Integer billingDueDay;

    @Column(name = "pppoe_status", length = 20)
    private String pppoeStatus = "unknown";

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "registration_date")
    private LocalDate registrationDate;

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
        if (isDeleted == null) isDeleted = false;
        if (isActive == null) isActive = true;
        if (pppoeStatus == null || pppoeStatus.isBlank()) pppoeStatus = "unknown";
        if (registrationDate == null) registrationDate = LocalDate.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

