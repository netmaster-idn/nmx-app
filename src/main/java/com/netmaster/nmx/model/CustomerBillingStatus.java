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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_billing_status")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerBillingStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @Column(name = "current_invoice_status", length = 30)
    private String currentInvoiceStatus;

    @Column(name = "current_payment_status", length = 30)
    private String currentPaymentStatus;

    @Column(name = "last_invoice_sent_at")
    private LocalDateTime lastInvoiceSentAt;

    @Column(name = "last_receipt_sent_at")
    private LocalDateTime lastReceiptSentAt;

    @Column(name = "overdue_days")
    private Integer overdueDays;

    @Column(name = "next_invoice_send_date")
    private LocalDate nextInvoiceSendDate;

    @Column(name = "eligible_for_disable", nullable = false)
    private Boolean eligibleForDisable = false;

    @Column(name = "pppoe_current_state", length = 20)
    private String pppoeCurrentState;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (eligibleForDisable == null) {
            eligibleForDisable = false;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
