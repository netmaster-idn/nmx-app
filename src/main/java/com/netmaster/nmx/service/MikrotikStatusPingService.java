package com.netmaster.nmx.service;

import com.netmaster.nmx.model.MikrotikDevice;
import com.netmaster.nmx.repository.MikrotikDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MikrotikStatusPingService {

    private static final long CHECK_INTERVAL_MS = 30L * 60L * 1000L;
    private static final int INITIAL_DELAY_MS = 15000;

    private final MikrotikDeviceRepository mikrotikRepository;
    private final MikrotikApiService mikrotikApiService;

    @Scheduled(fixedDelay = CHECK_INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    public void refreshDeviceStatuses() {
        List<MikrotikDevice> devices = mikrotikRepository.findByIsActiveTrue();
        log.info("Starting Mikrotik ping status check for {} devices", devices.size());

        for (MikrotikDevice device : devices) {
            refreshDeviceStatus(device);
        }
    }

    public MikrotikDevice refreshDeviceStatus(Long deviceId) {
        MikrotikDevice device = mikrotikRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device tidak ditemukan"));
        return refreshDeviceStatus(device);
    }

    public MikrotikDevice refreshDeviceStatus(MikrotikDevice device) {
        try {
            boolean reachable = mikrotikApiService.checkReachability(device);
            return applyStatus(device, reachable ? "online" : "offline");
        } catch (Exception ex) {
            log.warn("Failed to refresh Mikrotik status for {}: {}", device.resolveDeviceName(), ex.getMessage());
            return applyStatus(device, "offline");
        }
    }

    private MikrotikDevice applyStatus(MikrotikDevice device, String status) {
        device.applyCurrentStatus(status);
        device.setLastMonitored(LocalDateTime.now());
        device.setLastSeenAt(LocalDateTime.now());
        return mikrotikRepository.save(device);
    }
}
