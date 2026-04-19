package com.netmaster.nmx.service;

import com.netmaster.nmx.model.NetworkDevice;
import com.netmaster.nmx.model.DeviceMetrics;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SNMP Polling Service - Collects metrics from network devices using SNMP
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SnmpPollingService {

    private final NetworkDeviceRepository deviceRepository;
    private final DeviceMetricsRepository metricsRepository;

    // Common SNMP OIDs
    private static final String OID_SYS_DESCR = "1.3.6.1.2.1.1.1.0";
    private static final String OID_SYS_UPTIME = "1.3.6.1.2.1.1.3.0";
    private static final String OID_CPU_LOAD = "1.3.6.1.4.1.2021.10.1.5.1"; // laLoad.1
    private static final String OID_MEMORY_USED = "1.3.6.1.4.1.2021.4.6.0";
    private static final String OID_MEMORY_FREE = "1.3.6.1.4.1.2021.4.4.0";
    private static final String OID_IF_NUMBER = "1.3.6.1.2.1.2.1.0";
    private static final String OID_IF_DESCR = "1.3.6.1.2.1.2.2.1.2"; // ifDescr table
    private static final String OID_IF_STATUS = "1.3.6.1.2.1.2.2.1.8"; // ifOperStatus table
    private static final String OID_IF_IN_OCTETS = "1.3.6.1.2.1.2.2.1.10"; // ifInOctets
    private static final String OID_IF_OUT_OCTETS = "1.3.6.1.2.1.2.2.1.16"; // ifOutOctets
    private static final String OID_IF_SPEED = "1.3.6.1.2.1.2.2.1.5"; // ifSpeed

    // Cisco-specific OIDs
    private static final String OID_CISCO_CPU = "1.3.6.1.4.1.9.2.1.57.0";
    private static final String OID_CISCO_MEMORY = "1.3.6.1.4.1.9.9.48.1.1.5.1"; // ciscoMemoryPoolUsed

    // Juniper-specific OIDs
    private static final String OID_JUNIPER_CPU = "1.3.6.1.4.1.2636.3.1.13.1.5.6.0";

    /**
     * Poll all SNMP-enabled devices
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void pollAllDevices() {
        List<NetworkDevice> devices = deviceRepository.findDevicesToMonitor();
        log.info("Starting SNMP polling for {} devices", devices.size());

        for (NetworkDevice device : devices) {
            if (device.getSnmpCommunity() != null && !device.getSnmpCommunity().isEmpty()) {
                try {
                    pollDevice(device);
                } catch (Exception e) {
                    log.error("Error polling device {}: {}", device.getIpAddress(), e.getMessage());
                }
            }
        }
    }

    /**
     * Poll a single device
     */
    public DeviceMetrics pollDevice(NetworkDevice device) {
        String community = device.getSnmpCommunity() != null ? 
            device.getSnmpCommunity() : "public";
        
        Map<String, String> snmpData = snmpWalk(device.getIpAddress(), community, "1.3.6.1.2.1");
        if (snmpData.isEmpty()) {
            log.warn("Skipping SNMP metrics for {} because no database-backed monitoring data is available", device.getIpAddress());
            return null;
        }

        DeviceMetrics metrics = DeviceMetrics.builder()
                .device(device)
                .timestamp(LocalDateTime.now())
                .build();

        // Parse CPU
        if (snmpData.containsKey(OID_CPU_LOAD)) {
            try {
                BigDecimal cpu = new BigDecimal(snmpData.get(OID_CPU_LOAD))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                metrics.setCpuUsage(cpu);
            } catch (Exception e) {
                log.debug("Could not parse CPU for device {}", device.getIpAddress());
            }
        }

        // Parse memory
        if (snmpData.containsKey(OID_MEMORY_USED) && snmpData.containsKey(OID_MEMORY_FREE)) {
            try {
                long used = Long.parseLong(snmpData.get(OID_MEMORY_USED));
                long free = Long.parseLong(snmpData.get(OID_MEMORY_FREE));
                long total = used + free;
                BigDecimal usage = BigDecimal.valueOf(used * 100.0 / total)
                        .setScale(2, RoundingMode.HALF_UP);
                metrics.setMemoryUsage(usage);
                metrics.setMemoryUsed(used);
                metrics.setMemoryTotal(total);
            } catch (Exception e) {
                log.debug("Could not parse memory for device {}", device.getIpAddress());
            }
        }

        // Save metrics
        metrics = metricsRepository.save(metrics);

        // Update device last poll time
        device.setLastSnmpTime(LocalDateTime.now());
        deviceRepository.save(device);

        return metrics;
    }

    /**
     * Return empty data until real SNMP integration is available.
     */
    private Map<String, String> snmpWalk(String ipAddress, String community, String baseOid) {
        return Collections.emptyMap();
    }

    /**
     * Get single OID value
     */
    public String getSnmpValue(String ipAddress, String community, String oid) {
        // Placeholder - in production use SNMP4J
        return snmpWalk(ipAddress, community, oid).get(oid);
    }

    /**
     * Get interface statistics for a device
     */
    public Map<String, InterfaceStats> getInterfaceStats(NetworkDevice device) {
        String community = device.getSnmpCommunity() != null ? 
            device.getSnmpCommunity() : "public";
        
        Map<String, String> ifDescr = snmpWalk(device.getIpAddress(), community, OID_IF_DESCR);
        Map<String, String> ifStatus = snmpWalk(device.getIpAddress(), community, OID_IF_STATUS);
        Map<String, String> ifIn = snmpWalk(device.getIpAddress(), community, OID_IF_IN_OCTETS);
        Map<String, String> ifOut = snmpWalk(device.getIpAddress(), community, OID_IF_OUT_OCTETS);

        Map<String, InterfaceStats> interfaces = new HashMap<>();

        for (String index : ifDescr.keySet()) {
            InterfaceStats stats = new InterfaceStats();
            stats.setName(ifDescr.get(index));
            stats.setStatus(ifStatus.getOrDefault(index, "1").equals("1") ? "up" : "down");
            
            try {
                stats.setInOctets(ifIn.containsKey(index) ? Long.parseLong(ifIn.get(index)) : 0L);
                stats.setOutOctets(ifOut.containsKey(index) ? Long.parseLong(ifOut.get(index)) : 0L);
            } catch (NumberFormatException e) {
                stats.setInOctets(0L);
                stats.setOutOctets(0L);
            }
            
            interfaces.put(index, stats);
        }

        return interfaces;
    }

    /**
     * Interface statistics inner class
     */
    public static class InterfaceStats {
        private String name;
        private String status;
        private long inOctets;
        private long outOctets;
        private long inBps;
        private long outBps;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public long getInOctets() { return inOctets; }
        public void setInOctets(long inOctets) { this.inOctets = inOctets; }
        public long getOutOctets() { return outOctets; }
        public void setOutOctets(long outOctets) { this.outOctets = outOctets; }
        public long getInBps() { return inBps; }
        public void setInBps(long inBps) { this.inBps = inBps; }
        public long getOutBps() { return outBps; }
        public void setOutBps(long outBps) { this.outBps = outBps; }
    }
}

