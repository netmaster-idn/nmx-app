package com.netmaster.nmx.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "billing_automation_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BillingAutomationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auto_send_invoice", nullable = false)
    private Boolean autoSendInvoice = false;

    @Column(name = "invoice_send_days_before_due", nullable = false)
    private Integer invoiceSendDaysBeforeDue = 7;

    @Column(name = "auto_send_receipt", nullable = false)
    private Boolean autoSendReceipt = false;

    @Column(name = "auto_disable_pppoe", nullable = false)
    private Boolean autoDisablePppoe = false;

    @Column(name = "late_payment_disable_days", nullable = false)
    private Integer latePaymentDisableDays = 3;

    @Column(name = "disable_mode", length = 20, nullable = false)
    private String disableMode = "automatic";

    @Column(name = "send_warning_before_disable", nullable = false)
    private Boolean sendWarningBeforeDisable = true;

    @Column(name = "warning_template", columnDefinition = "TEXT")
    private String warningTemplate;

    @Column(name = "auto_enable_pppoe_after_payment", nullable = false)
    private Boolean autoEnablePppoeAfterPayment = true;

    @Column(name = "execution_time")
    private LocalTime executionTime;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (autoSendInvoice == null) {
            autoSendInvoice = false;
        }
        if (invoiceSendDaysBeforeDue == null || invoiceSendDaysBeforeDue < 0) {
            invoiceSendDaysBeforeDue = 7;
        }
        if (autoSendReceipt == null) {
            autoSendReceipt = false;
        }
        if (autoDisablePppoe == null) {
            autoDisablePppoe = false;
        }
        if (latePaymentDisableDays == null || latePaymentDisableDays < 0) {
            latePaymentDisableDays = 3;
        }
        if (disableMode == null || disableMode.isBlank()) {
            disableMode = "automatic";
        }
        if (sendWarningBeforeDisable == null) {
            sendWarningBeforeDisable = true;
        }
        if (autoEnablePppoeAfterPayment == null) {
            autoEnablePppoeAfterPayment = true;
        }
        if (executionTime == null) {
            executionTime = LocalTime.of(9, 0);
        }
        if (isActive == null) {
            isActive = true;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
