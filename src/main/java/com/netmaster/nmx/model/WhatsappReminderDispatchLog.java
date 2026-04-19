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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_reminder_dispatch_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WhatsappReminderDispatchLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "lead_days", nullable = false)
    private Integer leadDays;

    @Column(name = "scheduled_for_date", nullable = false)
    private LocalDate scheduledForDate;

    @Column(name = "dispatch_status", length = 20, nullable = false)
    private String dispatchStatus;

    @Column(name = "document_type", length = 30)
    private String documentType;

    @Column(name = "message_id", length = 160)
    private String messageId;

    @Column(name = "delivery_status", length = 30)
    private String deliveryStatus;

    @Column(name = "gateway_message", columnDefinition = "TEXT")
    private String gatewayMessage;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (dispatchStatus == null || dispatchStatus.isBlank()) {
            dispatchStatus = "pending";
        }
    }
}
