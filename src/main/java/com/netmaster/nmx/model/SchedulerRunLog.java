package com.netmaster.nmx.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "scheduler_run_logs",
        indexes = {
                @Index(name = "idx_scheduler_run_logs_job_name", columnList = "job_name"),
                @Index(name = "idx_scheduler_run_logs_started_at", columnList = "started_at"),
                @Index(name = "idx_scheduler_run_logs_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SchedulerRunLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", length = 80, nullable = false)
    private String jobName;

    @Column(name = "execution_mode", length = 20, nullable = false)
    private String executionMode;

    @Column(name = "status", length = 30, nullable = false)
    private String status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "processed_count", nullable = false)
    private Integer processedCount = 0;

    @Column(name = "success_count", nullable = false)
    private Integer successCount = 0;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        if (executionMode == null || executionMode.isBlank()) {
            executionMode = "system";
        }
        if (status == null || status.isBlank()) {
            status = "running";
        }
        if (processedCount == null) {
            processedCount = 0;
        }
        if (successCount == null) {
            successCount = 0;
        }
        if (failedCount == null) {
            failedCount = 0;
        }
    }
}
