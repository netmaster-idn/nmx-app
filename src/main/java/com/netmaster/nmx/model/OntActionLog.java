package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ont_action_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OntActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_service_id", nullable = false)
    private Long customerServiceId;

    @Column(name = "action_type", length = 40, nullable = false)
    private String actionType;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "status", length = 30)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null || status.isBlank()) {
            status = "queued";
        }
    }
}
