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

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "company_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanySetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_profile_id")
    private CompanyProfile companyProfile;

    @Column(name = "default_currency_code", length = 10)
    private String defaultCurrencyCode;

    @Column(name = "default_locale_code", length = 20)
    private String defaultLocaleCode;

    @Column(name = "default_tax_rate", precision = 5, scale = 2)
    private BigDecimal defaultTaxRate;

    @Column(name = "default_invoice_title", length = 100)
    private String defaultInvoiceTitle;

    @Column(name = "default_invoice_subtitle", length = 200)
    private String defaultInvoiceSubtitle;

    @Column(name = "default_footer_note", columnDefinition = "TEXT")
    private String defaultFooterNote;

    @Column(name = "whatsapp_reminder_enabled", nullable = false)
    private Boolean whatsappReminderEnabled = false;

    @Column(name = "whatsapp_reminder_lead_days")
    private Integer whatsappReminderLeadDays;

    @Column(name = "whatsapp_hourly_limit")
    private Integer whatsappHourlyLimit;

    @Column(name = "whatsapp_batch_interval_minutes")
    private Integer whatsappBatchIntervalMinutes;

    @Column(name = "whatsapp_send_start_hour")
    private Integer whatsappSendStartHour;

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
        if (isActive == null) {
            isActive = true;
        }
        if (defaultCurrencyCode == null || defaultCurrencyCode.isBlank()) {
            defaultCurrencyCode = "IDR";
        }
        if (defaultLocaleCode == null || defaultLocaleCode.isBlank()) {
            defaultLocaleCode = "id-ID";
        }
        if (defaultTaxRate == null) {
            defaultTaxRate = BigDecimal.ZERO;
        }
        if (defaultInvoiceTitle == null || defaultInvoiceTitle.isBlank()) {
            defaultInvoiceTitle = "INVOICE";
        }
        if (defaultInvoiceSubtitle == null || defaultInvoiceSubtitle.isBlank()) {
            defaultInvoiceSubtitle = "Document Payment Information";
        }
        if (defaultFooterNote == null || defaultFooterNote.isBlank()) {
            defaultFooterNote = "Data Belum di Set";
        }
        if (whatsappReminderEnabled == null) {
            whatsappReminderEnabled = false;
        }
        if (whatsappReminderLeadDays == null) {
            whatsappReminderLeadDays = 3;
        }
        if (whatsappHourlyLimit == null || whatsappHourlyLimit <= 0) {
            whatsappHourlyLimit = 6;
        }
        if (whatsappBatchIntervalMinutes == null || whatsappBatchIntervalMinutes <= 0) {
            whatsappBatchIntervalMinutes = 10;
        }
        if (whatsappSendStartHour == null || whatsappSendStartHour < 0 || whatsappSendStartHour > 23) {
            whatsappSendStartHour = 1;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
