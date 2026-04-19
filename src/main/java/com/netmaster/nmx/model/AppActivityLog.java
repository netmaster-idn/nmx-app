package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "full_name", length = 150)
    private String fullName;

    @Column(name = "role_names", length = 250)
    private String roleNames;

    @Column(name = "module_name", length = 80)
    private String moduleName;

    @Column(name = "action_type", length = 40)
    private String actionType;

    @Column(name = "action_label", length = 180)
    private String actionLabel;

    @Column(name = "http_method", length = 12)
    private String httpMethod;

    @Column(name = "request_path", length = 255)
    private String requestPath;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "ip_address", length = 80)
    private String ipAddress;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
