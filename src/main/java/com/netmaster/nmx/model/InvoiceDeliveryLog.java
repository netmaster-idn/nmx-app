package com.netmaster.nmx.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "invoice_delivery_logs",
        indexes = {
                @Index(name = "idx_invoice_delivery_logs_invoice", columnList = "invoice_id"),
                @Index(name = "idx_invoice_delivery_logs_customer", columnList = "customer_id"),
                @Index(name = "idx_invoice_delivery_logs_status", columnList = "status"),
                @Index(name = "idx_invoice_delivery_logs_sent_at", columnList = "sent_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "channel", length = 30, nullable = false)
    private String channel;

    @Column(name = "target", length = 100)
    private String target;

    @Column(name = "message_type", length = 30, nullable = false)
    private String messageType;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "provider_message_id", length = 160)
    private String providerMessageId;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "send_method", length = 30)
    private String sendMethod;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "sent_by", length = 100)
    private String sentBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
