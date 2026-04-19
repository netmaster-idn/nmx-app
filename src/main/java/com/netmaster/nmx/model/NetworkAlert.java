package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "network_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NetworkAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", length = 50, unique = true)
    private String alertId; // ALT-20260309-001

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Column(name = "device_type", length = 50)
    private String deviceType; // OLT, Router, Switch, Server, AP

    @Column(name = "device_ip", length = 45)
    private String deviceIp;

    @Column(name = "location", length = 100)
    private String location; // Site/POP name

    @Column(name = "alert_type", length = 50)
    private String alertType; // down, high_cpu, high_memory, high_traffic, temperature, voltage, link_down

    @Column(name = "metric_type", length = 50)
    private String metricType; // CPU, Memory, Bandwidth, Temperature, Optical Power

    @Column(name = "metric_value")
    private Double metricValue;

    @Column(name = "threshold")
    private Double threshold;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "severity", length = 20)
    private String severity; // critical, major, warning, info, resolved

    @Column(name = "status", length = 20)
    private String status; // active, acknowledged, investigating, resolved, closed

    @Column(name = "source", length = 20)
    private String source; // SNMP, API, Syslog, Ping

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "assigned_engineer", length = 100)
    private String assignedEngineer;

    @Column(name = "affected_customers")
    private Integer affectedCustomers;

    @Column(name = "affected_service", length = 100)
    private String affectedService; // Internet, VPN, VoIP

    @Column(name = "sla_impact", length = 20)
    private String slaImpact; // none, low, medium, high, critical

    @Column(name = "is_acknowledged")
    private boolean isAcknowledged = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private User acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_notes", length = 500)
    private String acknowledgedNotes;

    @Column(name = "is_resolved")
    private boolean isResolved = false;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_notes", length = 500)
    private String resolvedNotes;

    @Column(name = "is_closed")
    private boolean isClosed = false;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "investigation_notes", length = 1000)
    private String investigationNotes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "occurrence_count")
    private Integer occurrenceCount = 1;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (alertId == null) {
            alertId = generateAlertId();
        }
        if (status == null) {
            status = "active";
        }
        if (severity == null) {
            severity = "warning";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    private String generateAlertId() {
        String date = java.time.LocalDate.now().toString().replace("-", "");
        long count = System.currentTimeMillis() % 10000;
        return "ALT-" + date + "-" + String.format("%03d", count);
    }

    // Helper methods for status workflow
    public void acknowledge(User user, String notes) {
        this.isAcknowledged = true;
        this.acknowledgedBy = user;
        this.acknowledgedAt = LocalDateTime.now();
        this.acknowledgedNotes = notes;
        this.status = "acknowledged";
    }

    public void startInvestigating(String notes) {
        this.status = "investigating";
        if (this.investigationNotes != null) {
            this.investigationNotes += "\n" + LocalDateTime.now() + ": " + notes;
        } else {
            this.investigationNotes = LocalDateTime.now() + ": " + notes;
        }
    }

    public void resolve(String notes) {
        this.isResolved = true;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedNotes = notes;
        this.status = "resolved";
        this.severity = "resolved";
    }

    public void close() {
        this.isClosed = true;
        this.closedAt = LocalDateTime.now();
        this.status = "closed";
    }

    public void addInvestigationNote(String note) {
        if (this.investigationNotes != null) {
            this.investigationNotes += "\n" + LocalDateTime.now() + ": " + note;
        } else {
            this.investigationNotes = LocalDateTime.now() + ": " + note;
        }
    }

    public Long getResolutionTimeMinutes() {
        if (createdAt != null && resolvedAt != null) {
            return java.time.Duration.between(createdAt, resolvedAt).toMinutes();
        }
        return null;
    }
}

