package com.netmaster.nmx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.netmaster.nmx.config.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "mikrotik_devices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MikrotikDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "vpn_ip_address", length = 100)
    private String vpnIpAddress;

    @Column(name = "winbox_ip_address", length = 45)
    private String winboxIpAddress;

    @Column(name = "api_ip_address", length = 45)
    private String apiIpAddress;

    @Column(name = "api_port")
    private Integer apiPort = 8728;

    @Column(name = "api_username", length = 50)
    private String apiUsername;

    @Column(name = "api_password", length = 100)
    @Convert(converter = EncryptedStringConverter.class)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiPassword;

    @Column(name = "monitoring_target", length = 20)
    private String monitoringTarget = "vpn";

    @Column(name = "username", length = 50)
    private String username;

    @Column(name = "password", length = 100)
    @Convert(converter = EncryptedStringConverter.class)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "site_name", length = 200)
    private String siteName;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "status", length = 20)
    private String status = "offline"; // online, offline, maintenance

    @Column(name = "current_status", length = 20)
    private String currentStatus = "offline";

    @Column(name = "routerboard_version")
    private String routerboardVersion;

    @Column(name = "ros_version")
    private String rosVersion;

    @Column(name = "cpu_load")
    private Integer cpuLoad;

    @Column(name = "memory_used")
    private Long memoryUsed;

    @Column(name = "memory_total")
    private Long memoryTotal;

    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    @Column(name = "last_monitored")
    private LocalDateTime lastMonitored;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "last_snmp_sync_at")
    private LocalDateTime lastSnmpSyncAt;

    @Column(name = "last_api_sync_at")
    private LocalDateTime lastApiSyncAt;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "snmp_version", length = 20)
    private String snmpVersion = "2c";

    @Column(name = "snmp_community", length = 100)
    @Convert(converter = EncryptedStringConverter.class)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String snmpCommunity;

    @Column(name = "snmp_port")
    private Integer snmpPort = 161;

    @Column(name = "polling_interval_snmp")
    private Integer pollingIntervalSnmp = 60;

    @Column(name = "sync_interval_api")
    private Integer syncIntervalApi = 120;

    @Column(name = "snmp_enabled")
    private Boolean snmpEnabled = true;

    @Column(name = "api_enabled")
    private Boolean apiEnabled = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        syncCompatibilityFields();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        syncCompatibilityFields();
        updatedAt = LocalDateTime.now();
    }

    @PostLoad
    protected void onLoad() {
        syncCompatibilityFields();
    }

    public String resolveDeviceName() {
        return firstText(deviceName, name);
    }

    public String resolveSiteName() {
        return firstText(siteName, location);
    }

    public String resolveApiUsername() {
        return firstText(apiUsername, username);
    }

    public String resolveApiPassword() {
        return firstText(apiPassword, password);
    }

    public Integer resolveVpnPort() {
        String normalizedVpn = normalize(vpnIpAddress);
        if (normalizedVpn != null) {
            Integer parsedPort = extractEndpointPort(normalizedVpn);
            if (parsedPort != null) {
                return parsedPort;
            }
        }
        if (apiPort != null && apiPort > 0) {
            return apiPort;
        }
        return 8728;
    }

    public String resolveVpnHost() {
        return extractEndpointHost(vpnIpAddress);
    }

    public String resolveVpnEndpoint() {
        String normalizedVpn = normalize(vpnIpAddress);
        if (normalizedVpn == null) {
            return null;
        }
        String host = extractEndpointHost(normalizedVpn);
        if (host == null) {
            return null;
        }
        Integer port = extractEndpointPort(normalizedVpn);
        if (port != null) {
            return host + ":" + port;
        }
        Integer resolvedPort = apiPort != null && apiPort > 0 ? apiPort : null;
        return resolvedPort != null ? host + ":" + resolvedPort : host;
    }

    @Transient
    public String getVpnEndpoint() {
        return resolveVpnEndpoint();
    }

    @Transient
    public Integer getMonitoringInterval() {
        if (pollingIntervalSnmp != null && pollingIntervalSnmp > 0) {
            return pollingIntervalSnmp;
        }
        if (syncIntervalApi != null && syncIntervalApi > 0) {
            return syncIntervalApi;
        }
        return 60;
    }

    @Transient
    public String getApiPasswordMasked() {
        return maskSecret(resolveApiPassword());
    }

    @Transient
    public String getSnmpCommunityMasked() {
        return maskSecret(snmpCommunity);
    }

    public String resolveCurrentStatus() {
        return firstText(currentStatus, status, "offline");
    }

    public void applyDeviceName(String value) {
        String normalized = normalize(value);
        this.deviceName = normalized;
        this.name = normalized;
    }

    public void applySiteName(String value) {
        String normalized = normalize(value);
        this.siteName = normalized;
        this.location = normalized;
    }

    public void applyApiCredentials(String username, String password) {
        this.apiUsername = normalize(username);
        this.username = this.apiUsername;
        if (normalize(password) != null) {
            this.apiPassword = normalize(password);
            this.password = this.apiPassword;
        }
    }

    public void applyCurrentStatus(String value) {
        String normalized = normalize(value);
        String resolved = normalized != null ? normalized.toLowerCase(Locale.ROOT) : "offline";
        this.currentStatus = resolved;
        this.status = resolved;
    }

    private void syncCompatibilityFields() {
        if (normalize(deviceName) == null) {
            deviceName = normalize(name);
        }
        if (normalize(name) == null) {
            name = normalize(deviceName);
        }
        if (normalize(siteName) == null) {
            siteName = normalize(location);
        }
        if (normalize(location) == null) {
            location = normalize(siteName);
        }
        if (normalize(apiUsername) == null) {
            apiUsername = normalize(username);
        }
        if (normalize(username) == null) {
            username = normalize(apiUsername);
        }
        if (normalize(apiPassword) == null) {
            apiPassword = normalize(password);
        }
        if (normalize(password) == null) {
            password = normalize(apiPassword);
        }
        if (normalize(currentStatus) == null) {
            currentStatus = normalize(status);
        }
        if (normalize(status) == null) {
            status = normalize(currentStatus);
        }
        String normalizedVpn = normalize(vpnIpAddress);
        if (normalizedVpn != null) {
            String vpnHost = extractEndpointHost(normalizedVpn);
            Integer vpnPort = extractEndpointPort(normalizedVpn);
            if (vpnHost != null) {
                Integer normalizedPort = vpnPort != null ? vpnPort : (apiPort != null && apiPort > 0 ? apiPort : null);
                vpnIpAddress = normalizedPort != null ? vpnHost + ":" + normalizedPort : vpnHost;
            }
            if ((apiPort == null || apiPort < 1) && vpnPort != null) {
                apiPort = vpnPort;
            }
        }
        if (apiPort == null || apiPort < 1) {
            apiPort = 8728;
        }
        if (normalize(apiIpAddress) == null) {
            apiIpAddress = firstText(resolveVpnHost(), ipAddress);
        }
        if (snmpPort == null || snmpPort < 1) {
            snmpPort = 161;
        }
        if (pollingIntervalSnmp == null || pollingIntervalSnmp < 15) {
            pollingIntervalSnmp = 60;
        }
        if (syncIntervalApi == null || syncIntervalApi < 30) {
            syncIntervalApi = 120;
        }
        if (snmpVersion == null || snmpVersion.isBlank()) {
            snmpVersion = "2c";
        }
        if (snmpEnabled == null) {
            snmpEnabled = true;
        }
        if (apiEnabled == null) {
            apiEnabled = true;
        }
        if (monitoringTarget == null || monitoringTarget.isBlank()) {
            monitoringTarget = "vpn";
        } else {
            String normalizedTarget = monitoringTarget.trim().toLowerCase(Locale.ROOT);
            if (!normalizedTarget.equals("vpn") && !normalizedTarget.equals("api") && !normalizedTarget.equals("winbox")) {
                monitoringTarget = "vpn";
            } else {
                monitoringTarget = normalizedTarget;
            }
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String extractEndpointHost(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        int separatorIndex = normalized.lastIndexOf(':');
        if (separatorIndex > 0 && separatorIndex < normalized.length() - 1
                && normalized.indexOf(':') == separatorIndex) {
            return normalize(normalized.substring(0, separatorIndex));
        }
        return normalized;
    }

    private Integer extractEndpointPort(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        int separatorIndex = normalized.lastIndexOf(':');
        if (separatorIndex <= 0 || separatorIndex == normalized.length() - 1
                || normalized.indexOf(':') != separatorIndex) {
            return null;
        }
        try {
            int port = Integer.parseInt(normalized.substring(separatorIndex + 1));
            return port > 0 ? port : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String maskSecret(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() <= 4) {
            return "****";
        }
        return normalized.substring(0, 2) + "****" + normalized.substring(normalized.length() - 2);
    }
}

