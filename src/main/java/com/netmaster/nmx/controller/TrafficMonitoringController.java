package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.model.*;
import com.netmaster.nmx.repository.*;
import com.netmaster.nmx.service.MikrotikMonitoringManager;
import com.netmaster.nmx.service.MikrotikMonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/monitoring/trafik")
@RequiredArgsConstructor
public class TrafficMonitoringController {

    private final MikrotikMonitoringManager mikrotikMonitoringManager;
    private final MikrotikMonitoringService mikrotikMonitoringService;
    private final MikrotikDeviceRepository mikrotikDeviceRepository;
    private final MikrotikDeviceMetricRepository mikrotikDeviceMetricRepository;
    private final MikrotikInterfaceRepository mikrotikInterfaceRepository;
    private final MikrotikInterfaceTrafficRepository mikrotikInterfaceTrafficRepository;
    private final MikrotikPppoeSessionRepository mikrotikPppoeSessionRepository;
    private final MikrotikPppoeEventRepository mikrotikPppoeEventRepository;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary() {
        mikrotikMonitoringService.refreshMonitoringSnapshotIfNeeded();
        Map<String, Object> payload = new LinkedHashMap<>(mikrotikMonitoringManager.buildSummary());
        payload.put("sourceBadge", "Source: Winbox API");
        payload.put("sourceNote", "Winbox/API is the source of truth for device health and interface traffic");
        return ResponseEntity.ok(ApiResponse.success("Ringkasan trafik berhasil diambil", payload));
    }

    @GetMapping("/device-health")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getDeviceHealth() {
        mikrotikMonitoringService.refreshMonitoringSnapshotIfNeeded();
        List<MikrotikDevice> devices = mikrotikDeviceRepository.findByIsActiveTrue();
        List<MikrotikDeviceMetric> latestMetrics = devices.isEmpty()
                ? List.of()
                : mikrotikDeviceMetricRepository.findLatestByDeviceIds(devices.stream().map(MikrotikDevice::getId).toList());

        Map<Long, MikrotikDeviceMetric> metricMap = new LinkedHashMap<>();
        latestMetrics.forEach(metric -> metricMap.put(metric.getDevice().getId(), metric));

        List<Map<String, Object>> payload = devices.stream().map(device -> {
            MikrotikDeviceMetric metric = metricMap.get(device.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("deviceId", device.getId());
            row.put("deviceName", device.resolveDeviceName());
            row.put("siteName", device.resolveSiteName());
            row.put("status", device.resolveCurrentStatus());
            row.put("cpuLoad", metric != null ? metric.getCpuLoad() : null);
            row.put("memoryUsage", metric != null ? calculatePercent(metric.getMemoryUsed(), metric.getMemoryTotal()) : null);
            row.put("memoryUsed", metric != null ? metric.getMemoryUsed() : null);
            row.put("memoryTotal", metric != null ? metric.getMemoryTotal() : null);
            row.put("uptime", metric != null ? metric.getUptime() : null);
            row.put("temperature", metric != null ? metric.getTemperature() : null);
            row.put("voltage", metric != null ? metric.getVoltage() : null);
            row.put("lastSeenAt", device.getLastSeenAt());
            row.put("lastSnmpSyncAt", device.getLastSnmpSyncAt());
            row.put("source", "Winbox API");
            return row;
        }).toList();
        return ResponseEntity.ok(ApiResponse.success("Health panel berhasil diambil", payload));
    }

    @GetMapping("/interface-traffic")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInterfaceTraffic(
            @RequestParam(required = false, defaultValue = "6") Integer hours) {
        mikrotikMonitoringService.refreshMonitoringSnapshotIfNeeded();
        LocalDateTime since = LocalDateTime.now().minusHours(Math.max(hours, 1));
        List<Map<String, Object>> latestRows = mikrotikMonitoringService.buildLatestTrafficRows(since);

        List<MikrotikInterfaceTraffic> history = mikrotikInterfaceTrafficRepository.findRecentWithDeviceAndInterface(since);
        Map<String, long[]> timelineBuckets = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        for (int i = Math.max(hours, 1); i >= 0; i--) {
            LocalDateTime point = LocalDateTime.now().minusHours(i);
            timelineBuckets.put(point.format(formatter), new long[]{0L, 0L});
        }
        for (MikrotikInterfaceTraffic item : history) {
            String key = item.getCollectedAt().withMinute(0).withSecond(0).withNano(0).format(formatter);
            long[] bucket = timelineBuckets.computeIfAbsent(key, ignored -> new long[]{0L, 0L});
            bucket[0] += item.getInBps() != null ? item.getInBps() : 0L;
            bucket[1] += item.getOutBps() != null ? item.getOutBps() : 0L;
        }

        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("labels", new ArrayList<>(timelineBuckets.keySet()));
        chart.put("inbound", timelineBuckets.values().stream().map(bucket -> toMbps(bucket[0])).toList());
        chart.put("outbound", timelineBuckets.values().stream().map(bucket -> toMbps(bucket[1])).toList());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rows", latestRows);
        payload.put("chart", chart);
        payload.put("source", "Winbox API");
        payload.put("comment", "Traffic interface dibaca dari Winbox/API RouterOS dan disimpan ke cache database");
        return ResponseEntity.ok(ApiResponse.success("Traffic interface berhasil diambil", payload));
    }

    @GetMapping("/pppoe-sessions")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPppoeSessions() {
        mikrotikMonitoringService.refreshMonitoringSnapshotIfNeeded();
        List<Map<String, Object>> payload = mikrotikPppoeSessionRepository.findByStatusOrderByLastSyncAtDesc("active").stream()
                .map(session -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("deviceId", session.getDevice().getId());
                    row.put("deviceName", session.getDevice().resolveDeviceName());
                    row.put("username", session.getUsername());
                    row.put("ipAddress", session.getIpAddress());
                    row.put("callerId", session.getCallerId());
                    row.put("profileName", session.getProfileName());
                    row.put("status", session.getStatus());
                    row.put("loginAt", session.getLoginAt());
                    row.put("lastSyncAt", session.getLastSyncAt());
                    row.put("source", "MikroTik API");
                    return row;
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.success("PPPoE session aktif berhasil diambil", payload));
    }

    @GetMapping("/pppoe-events")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPppoeEvents() {
        mikrotikMonitoringService.refreshMonitoringSnapshotIfNeeded();
        List<Map<String, Object>> payload = mikrotikPppoeEventRepository.findTop100ByOrderByEventTimeDesc().stream()
                .map(event -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("deviceId", event.getDevice().getId());
                    row.put("deviceName", event.getDevice().resolveDeviceName());
                    row.put("username", event.getUsername());
                    row.put("eventType", event.getEventType());
                    row.put("ipAddress", event.getIpAddress());
                    row.put("callerId", event.getCallerId());
                    row.put("eventTime", event.getEventTime());
                    row.put("source", "MikroTik API");
                    return row;
                })
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Event PPPoE berhasil diambil", payload));
    }

    @GetMapping("/devices/{id}/detail")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDeviceDetail(@PathVariable Long id) {
        mikrotikMonitoringService.refreshMonitoringSnapshotIfNeeded();
        MikrotikDevice device = mikrotikDeviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Device tidak ditemukan"));
        MikrotikDeviceMetric latestMetric = mikrotikDeviceMetricRepository.findTopByDeviceIdOrderByCollectedAtDesc(id).orElse(null);

        List<Map<String, Object>> monitoringInterfaces = mikrotikInterfaceRepository.findByDeviceIdOrderByInterfaceNameAsc(id).stream()
                .map(iface -> {
                    MikrotikInterfaceTraffic latestTraffic = mikrotikInterfaceTrafficRepository
                            .findTopByMikrotikInterfaceIdOrderByCollectedAtDesc(iface.getId())
                            .orElse(null);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("interfaceName", iface.getInterfaceName());
                    row.put("adminStatus", iface.getAdminStatus());
                    row.put("operStatus", iface.getOperStatus());
                    row.put("inBps", latestTraffic != null ? latestTraffic.getInBps() : null);
                    row.put("outBps", latestTraffic != null ? latestTraffic.getOutBps() : null);
                    row.put("collectedAt", latestTraffic != null ? latestTraffic.getCollectedAt() : null);
                    row.put("source", "Winbox API");
                    return row;
                }).toList();

        List<Map<String, Object>> apiSessions = mikrotikPppoeSessionRepository.findByDeviceIdOrderByLastSyncAtDesc(id).stream()
                .map(session -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("username", session.getUsername());
                    row.put("ipAddress", session.getIpAddress());
                    row.put("callerId", session.getCallerId());
                    row.put("profileName", session.getProfileName());
                    row.put("status", session.getStatus());
                    row.put("loginAt", session.getLoginAt());
                    row.put("logoutAt", session.getLogoutAt());
                    row.put("lastSyncAt", session.getLastSyncAt());
                    row.put("source", "MikroTik API");
                    return row;
                }).toList();

        List<Map<String, Object>> apiEvents = mikrotikPppoeEventRepository.findTop100ByDeviceIdOrderByEventTimeDesc(id).stream()
                .map(event -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("username", event.getUsername());
                    row.put("eventType", event.getEventType());
                    row.put("ipAddress", event.getIpAddress());
                    row.put("eventTime", event.getEventTime());
                    row.put("source", "MikroTik API");
                    return row;
                }).toList();

        Map<String, Object> monitoringTab = new LinkedHashMap<>();
        monitoringTab.put("status", device.resolveCurrentStatus());
        monitoringTab.put("cpuLoad", latestMetric != null ? latestMetric.getCpuLoad() : null);
        monitoringTab.put("memoryUsage", latestMetric != null ? calculatePercent(latestMetric.getMemoryUsed(), latestMetric.getMemoryTotal()) : null);
        monitoringTab.put("uptime", latestMetric != null ? latestMetric.getUptime() : null);
        monitoringTab.put("temperature", latestMetric != null ? latestMetric.getTemperature() : null);
        monitoringTab.put("voltage", latestMetric != null ? latestMetric.getVoltage() : null);
        monitoringTab.put("lastSeenAt", device.getLastSeenAt());
        monitoringTab.put("interfaces", monitoringInterfaces);
        monitoringTab.put("source", "Source: Winbox API");

        Map<String, Object> apiTab = new LinkedHashMap<>();
        apiTab.put("sessions", apiSessions);
        apiTab.put("events", apiEvents);
        apiTab.put("source", "Source: MikroTik API");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceId", device.getId());
        payload.put("deviceName", device.resolveDeviceName());
        payload.put("siteName", device.resolveSiteName());
        payload.put("monitoring", monitoringTab);
        payload.put("apiSession", apiTab);
        return ResponseEntity.ok(ApiResponse.success("Detail device berhasil diambil", payload));
    }

    private Integer calculatePercent(Long used, Long total) {
        if (used == null || total == null || total <= 0L) {
            return null;
        }
        return BigDecimal.valueOf(used * 100.0d / total)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private Double toMbps(long value) {
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(1_000_000L), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
