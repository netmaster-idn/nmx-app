package com.netmaster.nmx.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "bank_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_profile_id")
    private CompanyProfile companyProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "account_name", length = 150)
    private String accountName;

    @Column(name = "account_number", length = 80)
    private String accountNumber;

    @Column(name = "branch_address", columnDefinition = "TEXT")
    private String branchAddress;

    @Column(name = "swift_code", length = 50)
    private String swiftCode;

    @Column(name = "payment_reference_label", length = 150)
    private String paymentReferenceLabel;

    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = false;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isPrimary == null) {
            isPrimary = false;
        }
        if (isActive == null) {
            isActive = true;
        }
        if (bankName == null || bankName.isBlank()) {
            bankName = "Data Belum di Set";
        }
        if (accountName == null || accountName.isBlank()) {
            accountName = "Data Belum di Set";
        }
        if (accountNumber == null || accountNumber.isBlank()) {
            accountNumber = "Data Belum di Set";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
