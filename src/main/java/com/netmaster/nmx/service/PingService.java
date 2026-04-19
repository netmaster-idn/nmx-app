package com.netmaster.nmx.service;

import com.netmaster.nmx.model.NetworkDevice;
import com.netmaster.nmx.model.NetworkDevice.DeviceStatus;
import com.netmaster.nmx.model.DeviceMetrics;
import com.netmaster.nmx.repository.NetworkDeviceRepository;
import com.netmaster.nmx.repository.DeviceMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ping Service - ICMP monitoring for network devices
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PingService {

    private static final Pattern WINDOWS_AVERAGE_PATTERN = Pattern.compile("average\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)\\s*ms");
    private static final Pattern UNIX_RTT_PATTERN = Pattern.compile("=\\s*[0-9]+(?:\\.[0-9]+)?/([0-9]+(?:\\.[0-9]+)?)/");
    private static final Pattern LATENCY_PATTERN = Pattern.compile("time[=<]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*ms");

    private final NetworkDeviceRepository deviceRepository;
    private final DeviceMetricsRepository metricsRepository;
    private final NetworkDeviceSyncStatusService syncStatusService;

    private static final int PING_TIMEOUT = 5; // seconds
    private static final int PING_COUNT = 4;

    /**
     * Ping all monitored devices
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void pingAllDevices() {
        List<NetworkDevice> devices = deviceRepository.findDevicesToMonitor();
        log.info("Starting ping check for {} devices", devices.size());

        for (NetworkDevice device : devices) {
            if (!syncStatusService.isExecutionAllowed(device, NetworkDeviceSyncStatusService.MODULE_PING_STATUS)) {
                log.debug("Skipping ping for {} because circuit breaker is active", device.getDeviceName());
                continue;
            }
            try {
                syncStatusService.recordAttempt(device, NetworkDeviceSyncStatusService.MODULE_PING_STATUS, 90);
                pingDevice(device);
            } catch (Exception e) {
                log.error("Error pinging device {}: {}", device.getIpAddress(), e.getMessage());
                syncStatusService.recordFailure(device, NetworkDeviceSyncStatusService.MODULE_PING_STATUS, e.getMessage(), 90);
                updateDeviceStatus(device, false, null);
            }
        }
    }

    /**
     * Ping a single device
     */
    public boolean pingDevice(NetworkDevice device) {
        long startedAt = System.currentTimeMillis();
        PingResult result = executePing(device.getIpAddress());
        
        boolean isReachable = result.isReachable();
        BigDecimal latency = result.getAverageLatency();
        
        updateDeviceStatus(device, isReachable, latency);
        
        // Save metrics
        if (isReachable) {
            saveMetrics(device, latency, BigDecimal.ZERO);
        }

        if (isReachable) {
            syncStatusService.recordSuccess(
                    device,
                    NetworkDeviceSyncStatusService.MODULE_PING_STATUS,
                    System.currentTimeMillis() - startedAt,
                    1,
                    90
            );
        } else {
            syncStatusService.recordFailure(
                    device,
                    NetworkDeviceSyncStatusService.MODULE_PING_STATUS,
                    "Ping unreachable",
                    90
            );
        }
        
        return isReachable;
    }

    /**
     * Execute ping command
     */
    private PingResult executePing(String ipAddress) {
        try {
            ProcessBuilder pb = new ProcessBuilder(buildPingCommand(
                    System.getProperty("os.name", ""),
                    PING_COUNT,
                    PING_TIMEOUT,
                    ipAddress
            ));
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean completed = process.waitFor(PING_TIMEOUT + 5, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
            }

            return parsePingOutput(output.toString());

        } catch (Exception e) {
            log.debug("Ping error for {}: {}", ipAddress, e.getMessage());
            return new PingResult(false, null);
        }
    }

    /**
     * Parse ping output to extract results
     */
    static List<String> buildPingCommand(String osName, int pingCount, int pingTimeoutSeconds, String ipAddress) {
        if (isWindows(osName)) {
            return List.of(
                    "ping",
                    "-n", String.valueOf(pingCount),
                    "-w", String.valueOf(pingTimeoutSeconds * 1000),
                    ipAddress
            );
        }

        return List.of(
                "ping",
                "-c", String.valueOf(pingCount),
                "-W", String.valueOf(Math.max(1, pingTimeoutSeconds)),
                ipAddress
        );
    }

    static PingResult parsePingOutput(String output) {
        String normalizedOutput = output == null ? "" : output.toLowerCase(Locale.ROOT);

        // Check if host is reachable
        if (normalizedOutput.contains("destination host unreachable")
                || normalizedOutput.contains("request timed out")
                || normalizedOutput.contains("100% loss")
                || normalizedOutput.contains("100% packet loss")) {
            return new PingResult(false, null);
        }

        BigDecimal averageLatency = extractLatency(normalizedOutput);
        if (averageLatency != null) {
            return new PingResult(true, averageLatency);
        }

        // If we got here, check if there was any response
        if (normalizedOutput.contains("reply from") || normalizedOutput.contains("bytes from")) {
            // Device is reachable but couldn't parse latency
            return new PingResult(true, BigDecimal.ZERO);
        }

        return new PingResult(false, null);
    }

    private static BigDecimal extractLatency(String normalizedOutput) {
        Matcher windowsAverage = WINDOWS_AVERAGE_PATTERN.matcher(normalizedOutput);
        if (windowsAverage.find()) {
            return new BigDecimal(windowsAverage.group(1));
        }

        Matcher unixRtt = UNIX_RTT_PATTERN.matcher(normalizedOutput);
        if (unixRtt.find()) {
            return new BigDecimal(unixRtt.group(1));
        }

        Matcher latencyMatcher = LATENCY_PATTERN.matcher(normalizedOutput);
        if (latencyMatcher.find()) {
            return new BigDecimal(latencyMatcher.group(1));
        }

        return null;
    }

    private static boolean isWindows(String osName) {
        return osName != null && osName.toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * Update device status based on ping result
     */
    private void updateDeviceStatus(NetworkDevice device, boolean reachable, BigDecimal latency) {
        DeviceStatus newStatus;
        
        if (reachable) {
            // Determine if online or warning based on latency
            if (latency != null && latency.intValue() > 100) {
                newStatus = DeviceStatus.WARNING;
            } else {
                newStatus = DeviceStatus.ONLINE;
            }
        } else {
            newStatus = DeviceStatus.OFFLINE;
        }

        boolean statusChanged = device.getStatus() != newStatus;
        
        device.setStatus(newStatus);
        device.setLastPingTime(LocalDateTime.now());
        
        if (reachable && latency != null) {
            // Store latency as a simple metric (not in device, but we track it)
        }
        
        deviceRepository.save(device);
        
        if (statusChanged) {
            log.info("Device {} status changed to {}", device.getDeviceName(), newStatus);
        }
    }

    /**
     * Save metrics after ping
     */
    private void saveMetrics(NetworkDevice device, BigDecimal latency, BigDecimal packetLoss) {
        DeviceMetrics metrics = DeviceMetrics.builder()
                .device(device)
                .latencyMs(latency)
                .packetLoss(packetLoss)
                .timestamp(LocalDateTime.now())
                .build();
        
        metricsRepository.save(metrics);
    }

    /**
     * Quick ping check for a single IP
     */
    public PingResult quickPing(String ipAddress) {
        return executePing(ipAddress);
    }

    /**
     * Inner class for ping results
     */
    public static class PingResult {
        private final boolean reachable;
        private final BigDecimal averageLatency;

        public PingResult(boolean reachable, BigDecimal averageLatency) {
            this.reachable = reachable;
            this.averageLatency = averageLatency;
        }

        public boolean isReachable() {
            return reachable;
        }

        public BigDecimal getAverageLatency() {
            return averageLatency;
        }
    }
}

