package com.netmaster.nmx.service;

import com.netmaster.nmx.model.NetworkDevice;
import com.netmaster.nmx.model.NetworkDeviceSyncStatus;
import com.netmaster.nmx.repository.NetworkDeviceSyncStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NetworkDeviceSyncStatusService {

    public static final String MODULE_PING_STATUS = "worker_ping_status";
    public static final String MODULE_DEVICE_HEALTH_SNAPSHOT = "worker_device_health_snapshot";
    public static final String MODULE_ALERT_EVALUATOR = "worker_alert_evaluator";
    public static final String MODULE_MAINTENANCE_RESOLVER = "worker_maintenance_state_resolver";

    private final NetworkDeviceSyncStatusRepository repository;

    @Transactional
    public boolean isExecutionAllowed(NetworkDevice device, String moduleName) {
        return repository.findByDeviceIdAndModuleName(device.getId(), moduleName)
                .map(NetworkDeviceSyncStatus::getBreakerUntil)
                .map(until -> until == null || until.isBefore(LocalDateTime.now()))
                .orElse(true);
    }

    @Transactional
    public void recordAttempt(NetworkDevice device, String moduleName, int staleAfterSeconds) {
        NetworkDeviceSyncStatus status = getOrCreate(device, moduleName);
        status.setLastAttemptAt(LocalDateTime.now());
        status.setStaleAfterSeconds(staleAfterSeconds);
        status.setStatus("running");
        repository.save(status);
    }

    @Transactional
    public void recordSuccess(NetworkDevice device, String moduleName, long durationMs, Integer itemCount, int staleAfterSeconds) {
        NetworkDeviceSyncStatus status = getOrCreate(device, moduleName);
        LocalDateTime now = LocalDateTime.now();
        status.setLastAttemptAt(now);
        status.setLastSuccessAt(now);
        status.setLastError(null);
        status.setFailCount(0);
        status.setBreakerUntil(null);
        status.setStatus("success");
        status.setStaleAfterSeconds(staleAfterSeconds);
        status.setLastDurationMs(durationMs);
        status.setLastItemCount(itemCount);
        repository.save(status);
    }

    @Transactional
    public void recordFailure(NetworkDevice device, String moduleName, String errorMessage, int staleAfterSeconds) {
        NetworkDeviceSyncStatus status = getOrCreate(device, moduleName);
        LocalDateTime now = LocalDateTime.now();
        int nextFailCount = (status.getFailCount() != null ? status.getFailCount() : 0) + 1;
        status.setLastAttemptAt(now);
        status.setLastError(trimMessage(errorMessage));
        status.setFailCount(nextFailCount);
        status.setStatus("failed");
        status.setStaleAfterSeconds(staleAfterSeconds);
        status.setBreakerUntil(now.plusSeconds(calculateBackoffSeconds(nextFailCount)));
        repository.save(status);
    }

    private NetworkDeviceSyncStatus getOrCreate(NetworkDevice device, String moduleName) {
        return repository.findByDeviceIdAndModuleName(device.getId(), moduleName)
                .orElseGet(() -> NetworkDeviceSyncStatus.builder()
                        .device(device)
                        .moduleName(moduleName)
                        .failCount(0)
                        .status("idle")
                        .build());
    }

    private long calculateBackoffSeconds(int failCount) {
        if (failCount <= 2) {
            return 0L;
        }
        long seconds = (long) Math.pow(2, Math.min(failCount - 2, 5)) * 30L;
        return Math.min(seconds, Duration.ofMinutes(10).toSeconds());
    }

    private String trimMessage(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
