package com.netmaster.nmx.service;

import com.netmaster.nmx.model.MikrotikDevice;
import com.netmaster.nmx.model.MikrotikInterfaceTraffic;
import com.netmaster.nmx.model.MikrotikPppoeSession;
import com.netmaster.nmx.repository.MikrotikDeviceRepository;
import com.netmaster.nmx.repository.MikrotikInterfaceTrafficRepository;
import com.netmaster.nmx.repository.MikrotikPppoeSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MikrotikMonitoringService {

    private final MikrotikDeviceRepository mikrotikDeviceRepository;
    private final MikrotikInterfaceTrafficRepository mikrotikInterfaceTrafficRepository;
    private final MikrotikPppoeSessionRepository mikrotikPppoeSessionRepository;
    private final MikrotikMonitoringManager mikrotikMonitoringManager;

    public void refreshMonitoringSnapshotIfNeeded() {
        // Request path intentionally does not trigger router polling anymore.
    }

    public void synchronizeLegacyDevices() {
        // Frontend reads cached database results, never directly from router.
        mikrotikDeviceRepository.findByIsActiveTrue().forEach(device -> {
            if (device.getDeviceName() == null || device.getDeviceName().isBlank()) {
                device.setDeviceName(device.getName());
            }
            if (device.getSiteName() == null || device.getSiteName().isBlank()) {
                device.setSiteName(device.getLocation());
            }
        });
    }

    public void deactivateSynchronizedDevice(Long mikrotikDeviceId) {
        mikrotikDeviceRepository.findById(mikrotikDeviceId).ifPresent(device -> {
            device.setActive(false);
            device.applyCurrentStatus("offline");
            mikrotikDeviceRepository.save(device);
        });
    }

    public List<Map<String, Object>> getTopActiveUsers(int limit) {
        List<MikrotikPppoeSession> sessions = mikrotikPppoeSessionRepository.findByStatusOrderByLastSyncAtDesc("active");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int index = 0; index < Math.min(limit, sessions.size()); index++) {
            MikrotikPppoeSession session = sessions.get(index);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("rank", index + 1);
            row.put("user", session.getUsername());
            row.put("ipAddress", session.getIpAddress());
            row.put("plan", session.getProfileName());
            row.put("deviceName", session.getDevice() != null ? session.getDevice().resolveDeviceName() : null);
            row.put("download", null);
            row.put("upload", null);
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> buildCachedTrafficSummary() {
        return mikrotikMonitoringManager.buildSummary();
    }

    public List<Map<String, Object>> buildLatestTrafficRows(LocalDateTime since) {
        Map<Long, MikrotikInterfaceTraffic> latestByInterface = new LinkedHashMap<>();
        mikrotikInterfaceTrafficRepository.findRecentWithDeviceAndInterface(since).forEach(traffic ->
                latestByInterface.putIfAbsent(traffic.getMikrotikInterface().getId(), traffic));

        return latestByInterface.values().stream()
                .sorted(Comparator.comparingLong((MikrotikInterfaceTraffic item) -> safeLong(item.getInBps()) + safeLong(item.getOutBps())).reversed())
                .map(traffic -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("interfaceId", traffic.getMikrotikInterface().getId());
                    row.put("interfaceName", traffic.getMikrotikInterface().getInterfaceName());
                    row.put("deviceId", traffic.getDevice().getId());
                    row.put("deviceName", traffic.getDevice().resolveDeviceName());
                    row.put("deviceLocation", traffic.getDevice().resolveSiteName());
                    row.put("status", traffic.getMikrotikInterface().getOperStatus());
                    row.put("inBps", traffic.getInBps());
                    row.put("outBps", traffic.getOutBps());
                    row.put("inMbps", toMbps(traffic.getInBps()));
                    row.put("outMbps", toMbps(traffic.getOutBps()));
                    row.put("totalMbps", toMbps(safeLong(traffic.getInBps()) + safeLong(traffic.getOutBps())));
                    row.put("collectedAt", traffic.getCollectedAt());
                    row.put("source", "Winbox API");
                    return row;
                })
                .toList();
    }

    private long safeLong(Long value) {
        return value != null ? value : 0L;
    }

    private Double toMbps(Long bps) {
        if (bps == null) {
            return null;
        }
        return BigDecimal.valueOf(bps)
                .divide(BigDecimal.valueOf(1_000_000L), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
