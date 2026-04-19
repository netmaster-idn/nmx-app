package com.netmaster.nmx.service;

import com.netmaster.nmx.model.NetworkDevice;
import com.netmaster.nmx.model.DeviceMetrics;
import com.netmaster.nmx.model.NetworkDevice.DeviceStatus;
import com.netmaster.nmx.model.NetworkDevice.DeviceType;
import com.netmaster.nmx.repository.NetworkDeviceRepository;
import com.netmaster.nmx.repository.DeviceMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * OLT Monitoring Service
 * Monitors OLT devices and their PON ports/ONUs
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OltMonitoringService {

    private final NetworkDeviceRepository deviceRepository;
    private final DeviceMetricsRepository metricsRepository;

    /**
     * Monitor all OLT devices
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void monitorAllOltDevices() {
        List<NetworkDevice> devices = deviceRepository.findByDeviceTypeAndIsActiveTrue(DeviceType.OLT);
        log.info("Starting OLT monitoring for {} devices", devices.size());

        for (NetworkDevice device : devices) {
            if (device.getIsMonitored()) {
                try {
                    monitorOltDevice(device);
                } catch (Exception e) {
                    log.error("Error monitoring OLT {}: {}", device.getIpAddress(), e.getMessage());
                    device.setStatus(DeviceStatus.OFFLINE);
                    deviceRepository.save(device);
                }
            }
        }
    }

    /**
     * Monitor a single OLT device
     */
    public DeviceMetrics monitorOltDevice(NetworkDevice device) {
        // In production, connect via SNMP or OLT vendor API (Huawei, ZTE, FiberHome, etc.)
        
        Map<String, Object> oltInfo = getOltInfo(device);
        List<Map<String, Object>> ponPorts = getPonPorts(device);
        Map<String, Object> systemResources = getSystemResources(device);
        if (oltInfo.isEmpty() && ponPorts.isEmpty() && systemResources.isEmpty()) {
            log.warn("Skipping OLT metrics for {} because no database-backed monitoring data is available", device.getIpAddress());
            return null;
        }

        DeviceMetrics metrics = DeviceMetrics.builder()
                .device(device)
                .timestamp(LocalDateTime.now())
                .build();

        // Parse system resources
        if (systemResources.containsKey("cpu")) {
            metrics.setCpuUsage(new BigDecimal(systemResources.get("cpu").toString())
                    .setScale(2, RoundingMode.HALF_UP));
        }

        if (systemResources.containsKey("memory")) {
            metrics.setMemoryUsage(new BigDecimal(systemResources.get("memory").toString())
                    .setScale(2, RoundingMode.HALF_UP));
        }

        if (systemResources.containsKey("temperature")) {
            metrics.setTemperature(new BigDecimal(systemResources.get("temperature").toString())
                    .setScale(2, RoundingMode.HALF_UP));
        }

        // Calculate ONU statistics
        int totalOnu = 0;
        int onlineOnu = 0;
        int offlineOnu = 0;

        for (Map<String, Object> port : ponPorts) {
            int portTotal = Integer.parseInt(port.getOrDefault("onuCount", "0").toString());
            int portOnline = Integer.parseInt(port.getOrDefault("onuOnline", "0").toString());
            
            totalOnu += portTotal;
            onlineOnu += portOnline;
            offlineOnu += (portTotal - portOnline);
        }

        metrics.setOnuTotal(totalOnu);
        metrics.setOnuOnline(onlineOnu);
        metrics.setOnuOffline(offlineOnu);

        // Calculate aggregate traffic
        long totalRx = ponPorts.stream()
                .mapToLong(p -> Long.parseLong(p.getOrDefault("rxBytes", "0").toString()))
                .sum();
        long totalTx = ponPorts.stream()
                .mapToLong(p -> Long.parseLong(p.getOrDefault("txBytes", "0").toString()))
                .sum();

        metrics.setTrafficRxBytes(totalRx);
        metrics.setTrafficTxBytes(totalTx);

        // Determine status
        DeviceStatus status = determineStatus(metrics, device, offlineOnu);
        device.setStatus(status);
        device.setLastSnmpTime(LocalDateTime.now());
        
        deviceRepository.save(device);
        
        return metricsRepository.save(metrics);
    }

    /**
     * Return empty data until real OLT integration is available.
     */
    private Map<String, Object> getOltInfo(NetworkDevice device) {
        return Collections.emptyMap();
    }

    /**
     * Return empty data until real OLT integration is available.
     */
    private List<Map<String, Object>> getPonPorts(NetworkDevice device) {
        return Collections.emptyList();
    }

    /**
     * Return empty data until real OLT integration is available.
     */
    private Map<String, Object> getSystemResources(NetworkDevice device) {
        return Collections.emptyMap();
    }

    /**
     * Determine OLT status
     */
    private DeviceStatus determineStatus(DeviceMetrics metrics, NetworkDevice device, int offlineOnu) {
        // Check CPU
        if (metrics.getCpuUsage() != null && 
            metrics.getCpuUsage().intValue() >= device.getCpuCriticalThreshold()) {
            return DeviceStatus.WARNING;
        }

        // Check Memory
        if (metrics.getMemoryUsage() != null && 
            metrics.getMemoryUsage().intValue() >= device.getMemoryCriticalThreshold()) {
            return DeviceStatus.WARNING;
        }

        // Check Temperature
        if (metrics.getTemperature() != null && 
            metrics.getTemperature().intValue() >= device.getTemperatureCriticalThreshold()) {
            return DeviceStatus.WARNING;
        }

        // Check offline ONUs
        if (offlineOnu > 0) {
            return DeviceStatus.WARNING;
        }

        return DeviceStatus.ONLINE;
    }

    /**
     * Get detailed PON port information
     */
    public List<Map<String, Object>> getPonPortDetails(NetworkDevice device) {
        return getPonPorts(device);
    }

    /**
     * Get optical power levels for all PON ports
     */
    public Map<Integer, Double> getOpticalPowerLevels(NetworkDevice device) {
        List<Map<String, Object>> ports = getPonPorts(device);
        Map<Integer, Double> levels = new HashMap<>();
        
        for (Map<String, Object> port : ports) {
            int portNum = Integer.parseInt(port.get("portNumber").toString());
            double power = Double.parseDouble(port.get("opticalRx").toString());
            levels.put(portNum, power);
        }
        
        return levels;
    }

    /**
     * Get ONU details for a specific PON port
     */
    public List<Map<String, Object>> getOnuList(NetworkDevice device, int ponPort) {
        return Collections.emptyList();
    }
}

