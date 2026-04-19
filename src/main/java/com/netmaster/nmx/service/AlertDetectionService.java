package com.netmaster.nmx.service;

import com.netmaster.nmx.model.NetworkAlert;
import com.netmaster.nmx.model.NetworkDevice;
import com.netmaster.nmx.model.DeviceMetrics;
import com.netmaster.nmx.model.NetworkDevice.DeviceStatus;
import com.netmaster.nmx.model.NetworkDevice.DeviceType;
import com.netmaster.nmx.repository.DeviceMaintenanceWindowRepository;
import com.netmaster.nmx.repository.NetworkAlertRepository;
import com.netmaster.nmx.repository.NetworkDeviceRepository;
import com.netmaster.nmx.repository.DeviceMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Alert Detection Service
 * Automatically detects and creates alerts based on device metrics and thresholds
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertDetectionService {

    private final NetworkAlertRepository alertRepository;
    private final NetworkDeviceRepository deviceRepository;
    private final DeviceMetricsRepository metricsRepository;
    private final DeviceMaintenanceWindowRepository maintenanceWindowRepository;
    private final NetworkDeviceSyncStatusService syncStatusService;

    // Alert cooldown period (in minutes) - prevent duplicate alerts
    private static final int ALERT_COOLDOWN_MINUTES = 15;
    
    // Track recent alerts to prevent duplicates
    private final Map<String, LocalDateTime> recentAlerts = new HashMap<>();

    /**
     * Run alert detection every minute
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 20000)
    public void detectAlerts() {
        log.debug("Running alert detection...");
        
        List<NetworkDevice> devices = deviceRepository.findByIsMonitoredTrueAndIsActiveTrue();
        
        for (NetworkDevice device : devices) {
            if (!syncStatusService.isExecutionAllowed(device, NetworkDeviceSyncStatusService.MODULE_ALERT_EVALUATOR)) {
                continue;
            }
            try {
                syncStatusService.recordAttempt(device, NetworkDeviceSyncStatusService.MODULE_ALERT_EVALUATOR, 180);
                detectDeviceAlerts(device);
                syncStatusService.recordSuccess(device, NetworkDeviceSyncStatusService.MODULE_ALERT_EVALUATOR, 0L, 1, 180);
            } catch (Exception e) {
                log.error("Error detecting alerts for device {}: {}", device.getDeviceName(), e.getMessage());
                syncStatusService.recordFailure(device, NetworkDeviceSyncStatusService.MODULE_ALERT_EVALUATOR, e.getMessage(), 180);
            }
        }
        
        // Clean up old alert tracking
        cleanupAlertTracking();
    }

    /**
     * Detect alerts for a specific device
     */
    public void detectDeviceAlerts(NetworkDevice device) {
        if (maintenanceWindowRepository.findActiveWindowByDeviceId(device.getId(), LocalDateTime.now()).isPresent()) {
            log.debug("Skipping alert evaluation for {} because maintenance window is active", device.getDeviceName());
            return;
        }

        // 1. Check device offline status
        if (device.getStatus() == DeviceStatus.OFFLINE) {
            checkDeviceOffline(device);
        }

        // 2. Get latest metrics and check thresholds
        Optional<DeviceMetrics> latestMetrics = metricsRepository
                .findTopByDeviceIdOrderByTimestampDesc(device.getId());
        
        if (latestMetrics.isPresent()) {
            DeviceMetrics metrics = latestMetrics.get();
            
            // Check CPU
            if (metrics.getCpuUsage() != null) {
                checkCpuThreshold(device, metrics.getCpuUsage());
            }
            
            // Check Memory
            if (metrics.getMemoryUsage() != null) {
                checkMemoryThreshold(device, metrics.getMemoryUsage());
            }
            
            // Check Temperature
            if (metrics.getTemperature() != null) {
                checkTemperatureThreshold(device, metrics.getTemperature());
            }
            
            // Check Packet Loss
            if (metrics.getPacketLoss() != null) {
                checkPacketLoss(device, metrics.getPacketLoss());
            }
            
            // Check OLT-specific metrics
            if (device.getDeviceType() == DeviceType.OLT) {
                checkOltAlerts(device, metrics);
            }
            
            // Check Mikrotik-specific metrics
            if (device.getDeviceType() == DeviceType.MIKROTIK) {
                checkMikrotikAlerts(device, metrics);
            }
        }
    }

    /**
     * Check if device went offline
     */
    private void checkDeviceOffline(NetworkDevice device) {
        String alertKey = "offline-" + device.getId();
        
        if (!hasRecentAlert(alertKey)) {
            // Check if this is a new offline event
            if (device.getLastPingTime() != null && 
                device.getLastPingTime().isBefore(LocalDateTime.now().minusMinutes(5))) {
                
                createAlert(device, "critical", "device_offline", "CRITICAL",
                    String.format("Device %s (%s) is offline", device.getDeviceName(), device.getIpAddress()));
                
                recentAlerts.put(alertKey, LocalDateTime.now());
            }
        }
    }

    /**
     * Check CPU threshold
     */
    private void checkCpuThreshold(NetworkDevice device, java.math.BigDecimal cpuUsage) {
        int cpu = cpuUsage.intValue();
        
        String criticalKey = "cpu-critical-" + device.getId();
        String warningKey = "cpu-warning-" + device.getId();
        
        if (cpu >= device.getCpuCriticalThreshold()) {
            if (!hasRecentAlert(criticalKey)) {
                createAlert(device, "critical", "high_cpu", "CRITICAL",
                    String.format("CPU usage critical: %d%% on %s", cpu, device.getDeviceName()),
                    cpuUsage.doubleValue(), (double) device.getCpuCriticalThreshold());
                recentAlerts.put(criticalKey, LocalDateTime.now());
            }
        } else if (cpu >= device.getCpuWarningThreshold()) {
            if (!hasRecentAlert(warningKey)) {
                createAlert(device, "warning", "high_cpu", "WARNING",
                    String.format("CPU usage high: %d%% on %s", cpu, device.getDeviceName()),
                    cpuUsage.doubleValue(), (double) device.getCpuWarningThreshold());
                recentAlerts.put(warningKey, LocalDateTime.now());
            }
        } else {
            // Clear previous CPU alerts
            clearAlert(criticalKey);
            clearAlert(warningKey);
        }
    }

    /**
     * Check Memory threshold
     */
    private void checkMemoryThreshold(NetworkDevice device, java.math.BigDecimal memoryUsage) {
        int memory = memoryUsage.intValue();
        
        String criticalKey = "mem-critical-" + device.getId();
        String warningKey = "mem-warning-" + device.getId();
        
        if (memory >= device.getMemoryCriticalThreshold()) {
            if (!hasRecentAlert(criticalKey)) {
                createAlert(device, "critical", "high_memory", "CRITICAL",
                    String.format("Memory usage critical: %d%% on %s", memory, device.getDeviceName()),
                    memoryUsage.doubleValue(), (double) device.getMemoryCriticalThreshold());
                recentAlerts.put(criticalKey, LocalDateTime.now());
            }
        } else if (memory >= device.getMemoryWarningThreshold()) {
            if (!hasRecentAlert(warningKey)) {
                createAlert(device, "warning", "high_memory", "WARNING",
                    String.format("Memory usage high: %d%% on %s", memory, device.getDeviceName()),
                    memoryUsage.doubleValue(), (double) device.getMemoryWarningThreshold());
                recentAlerts.put(warningKey, LocalDateTime.now());
            }
        } else {
            clearAlert(criticalKey);
            clearAlert(warningKey);
        }
    }

    /**
     * Check Temperature threshold
     */
    private void checkTemperatureThreshold(NetworkDevice device, java.math.BigDecimal temperature) {
        int temp = temperature.intValue();
        
        String criticalKey = "temp-critical-" + device.getId();
        String warningKey = "temp-warning-" + device.getId();
        
        if (temp >= device.getTemperatureCriticalThreshold()) {
            if (!hasRecentAlert(criticalKey)) {
                createAlert(device, "critical", "high_temperature", "CRITICAL",
                    String.format("Temperature critical: %d°C on %s", temp, device.getDeviceName()),
                    temperature.doubleValue(), (double) device.getTemperatureCriticalThreshold());
                recentAlerts.put(criticalKey, LocalDateTime.now());
            }
        } else if (temp >= device.getTemperatureWarningThreshold()) {
            if (!hasRecentAlert(warningKey)) {
                createAlert(device, "warning", "high_temperature", "WARNING",
                    String.format("Temperature high: %d°C on %s", temp, device.getDeviceName()),
                    temperature.doubleValue(), (double) device.getTemperatureWarningThreshold());
                recentAlerts.put(warningKey, LocalDateTime.now());
            }
        } else {
            clearAlert(criticalKey);
            clearAlert(warningKey);
        }
    }

    /**
     * Check Packet Loss
     */
    private void checkPacketLoss(NetworkDevice device, java.math.BigDecimal packetLoss) {
        double loss = packetLoss.doubleValue();
        
        String criticalKey = "loss-critical-" + device.getId();
        String warningKey = "loss-warning-" + device.getId();
        
        if (loss >= 5.0) {
            if (!hasRecentAlert(criticalKey)) {
                createAlert(device, "critical", "high_packet_loss", "CRITICAL",
                    String.format("Packet loss critical: %.1f%% on %s", loss, device.getDeviceName()),
                    loss, 5.0);
                recentAlerts.put(criticalKey, LocalDateTime.now());
            }
        } else if (loss >= 1.0) {
            if (!hasRecentAlert(warningKey)) {
                createAlert(device, "warning", "high_packet_loss", "WARNING",
                    String.format("Packet loss detected: %.1f%% on %s", loss, device.getDeviceName()),
                    loss, 1.0);
                recentAlerts.put(warningKey, LocalDateTime.now());
            }
        } else {
            clearAlert(criticalKey);
            clearAlert(warningKey);
        }
    }

    /**
     * Check OLT-specific alerts
     */
    private void checkOltAlerts(NetworkDevice device, DeviceMetrics metrics) {
        // Check offline ONUs
        if (metrics.getOnuOffline() != null && metrics.getOnuOffline() > 0) {
            String alertKey = "olt-onu-offline-" + device.getId();
            
            if (!hasRecentAlert(alertKey)) {
                int offlineCount = metrics.getOnuOffline();
                String severity = offlineCount > 10 ? "critical" : "warning";
                
                createAlert(device, severity, "onu_offline", severity.toUpperCase(),
                    String.format("%d ONU devices offline on OLT %s", offlineCount, device.getDeviceName()),
                    (double) offlineCount, 0.0);
                
                recentAlerts.put(alertKey, LocalDateTime.now());
            }
        }
        
        // Check optical power
        if (metrics.getOpticalRxPower() != null) {
            double rxPower = metrics.getOpticalRxPower().doubleValue();
            
            String criticalKey = "olt-rx-critical-" + device.getId();
            
            if (rxPower < -28.0 && !hasRecentAlert(criticalKey)) {
                createAlert(device, "critical", "low_optical_power", "CRITICAL",
                    String.format("Optical RX power critical: %.2f dBm on %s", rxPower, device.getDeviceName()),
                    rxPower, -28.0);
                recentAlerts.put(criticalKey, LocalDateTime.now());
            }
        }
    }

    /**
     * Check Mikrotik-specific alerts
     */
    private void checkMikrotikAlerts(NetworkDevice device, DeviceMetrics metrics) {
        // High session count
        if (metrics.getActiveSessions() != null && metrics.getActiveSessions() > 1000) {
            String alertKey = "mikrotik-sessions-" + device.getId();
            
            if (!hasRecentAlert(alertKey)) {
                createAlert(device, "warning", "high_sessions", "WARNING",
                    String.format("High session count: %d on %s", metrics.getActiveSessions(), device.getDeviceName()),
                    (double) metrics.getActiveSessions(), 1000.0);
                
                recentAlerts.put(alertKey, LocalDateTime.now());
            }
        }
    }

    /**
     * Create a new alert
     */
    private void createAlert(NetworkDevice device, String severity, String alertType, String severityLevel,
                           String message) {
        createAlert(device, severity, alertType, severityLevel, message, null, null);
    }

    /**
     * Create a new alert with metric values
     */
    private void createAlert(NetworkDevice device, String severity, String alertType, String severityLevel,
                           String message, Double metricValue, Double threshold) {
        
        // Check if similar alert already exists and is active
        List<NetworkAlert> existingAlerts = alertRepository.findActiveAlerts();
        Optional<NetworkAlert> existing = existingAlerts.stream()
            .filter(a -> a.getDeviceId() != null && a.getDeviceId().equals(device.getId()))
            .filter(a -> alertType.equals(a.getAlertType()))
            .findFirst();
        
        if (existing.isPresent()) {
            // Update occurrence count and continue
            NetworkAlert alert = existing.get();
            alert.setOccurrenceCount(alert.getOccurrenceCount() + 1);
            alert.setDurationMinutes(alert.getDurationMinutes() + 1);
            alertRepository.save(alert);
            return;
        }
        
        // Create new alert
        NetworkAlert alert = new NetworkAlert();
        alert.setDeviceId(device.getId());
        alert.setDeviceName(device.getDeviceName());
        alert.setDeviceType(device.getDeviceType().name());
        alert.setDeviceIp(device.getIpAddress());
        alert.setLocation(device.getLocation());
        alert.setAlertType(alertType);
        alert.setSeverity(severity == null ? "warning" : severity.toLowerCase());
        alert.setStatus("active");
        alert.setMessage(message);
        alert.setMetricValue(metricValue);
        alert.setThreshold(threshold);
        alert.setSource("auto");
        alert.setDurationMinutes(1);
        alert.setOccurrenceCount(1);
        
        alertRepository.save(alert);
        
        log.info("Alert created: {} - {} ({})", severityLevel, device.getDeviceName(), alertType);
    }

    /**
     * Check if there's a recent alert for this key
     */
    private boolean hasRecentAlert(String alertKey) {
        LocalDateTime lastAlert = recentAlerts.get(alertKey);
        if (lastAlert == null) {
            return false;
        }
        
        return lastAlert.isAfter(LocalDateTime.now().minusMinutes(ALERT_COOLDOWN_MINUTES));
    }

    /**
     * Clear an alert from tracking (when condition is resolved)
     */
    private void clearAlert(String alertKey) {
        recentAlerts.remove(alertKey);
    }

    /**
     * Clean up old alert tracking entries
     */
    private void cleanupAlertTracking() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        recentAlerts.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    /**
     * Resolve alerts for a device when it comes back online
     */
    public void resolveAlertsForDevice(Long deviceId) {
        List<NetworkAlert> activeAlerts = alertRepository.findActiveAlerts();
        
        activeAlerts.stream()
            .filter(a -> a.getDeviceId() != null && a.getDeviceId().equals(deviceId))
            .forEach(alert -> {
                alert.setResolved(true);
                alert.setResolvedAt(LocalDateTime.now());
                alert.setStatus("resolved");
                alert.setResolvedNotes("Device is now online");
                alertRepository.save(alert);
            });
    }
}

