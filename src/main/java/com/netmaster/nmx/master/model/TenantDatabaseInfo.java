package com.netmaster.nmx.master.model;

import com.netmaster.nmx.config.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenant_databases")
@Getter
@Setter
public class TenantDatabaseInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private Long tenantId;

    @Column(name = "db_name", nullable = false, unique = true, length = 120)
    private String dbName;

    @Column(name = "db_host", nullable = false, length = 120)
    private String dbHost;

    @Column(name = "db_port", nullable = false)
    private Integer dbPort;

    @Column(name = "db_user", nullable = false, length = 120)
    private String dbUser;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "db_password_encrypted", nullable = false, length = 500)
    private String dbPasswordEncrypted;

    @Column(name = "jdbc_url", nullable = false, length = 500)
    private String jdbcUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
