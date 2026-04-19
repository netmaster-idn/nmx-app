package com.netmaster.nmx.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "acs_settings")
@Getter
@Setter
@NoArgsConstructor
public class AcsSettings {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "server_url", length = 255)
    private String serverUrl;

    @Column(name = "username", length = 120)
    private String username;

    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "last_connection_status", length = 30)
    private String lastConnectionStatus;

    @Column(name = "last_connection_message", columnDefinition = "TEXT")
    private String lastConnectionMessage;

    @Column(name = "last_connected_at")
    private LocalDateTime lastConnectedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) {
            id = SINGLETON_ID;
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
