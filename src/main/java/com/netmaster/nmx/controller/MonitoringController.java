package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.model.DeviceMetrics;
import com.netmaster.nmx.model.MikrotikDevice;
import com.netmaster.nmx.model.NetworkAlert;
import com.netmaster.nmx.model.NetworkDevice;
import com.netmaster.nmx.model.OltDevice;
import com.netmaster.nmx.model.Server;
import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import com.netmaster.nmx.repository.DeviceMetricsRepository;
import com.netmaster.nmx.repository.MikrotikDeviceRepository;
import com.netmaster.nmx.repository.NetworkAlertRepository;
import com.netmaster.nmx.repository.NetworkDeviceRepository;
import com.netmaster.nmx.repository.OltDeviceRepository;
import com.netmaster.nmx.repository.RegionRepository;
import com.netmaster.nmx.repository.ServerRepository;
import com.netmaster.nmx.service.MikrotikConnectionService;
import com.netmaster.nmx.service.MikrotikMonitoringService;
import com.netmaster.nmx.service.MikrotikRouterOsApiClient;
import com.netmaster.nmx.service.MikrotikWanTrafficCollectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringController {

    private static final String EMPTY_DATA_FROM_DATABASE = "Empty Data From Database";

    private final NetworkAlertRepository alertRepository;
    private final MikrotikDeviceRepository mikrotikRepository;
    private final OltDeviceRepository oltRepository;
    private final RegionRepository regionRepository;
    private final CustomerRepository customerRepository;
    private final NetworkDeviceRepository networkDeviceRepository;
    private final DeviceMetricsRepository deviceMetricsRepository;
    private final CustomerServiceEntityRepository customerServiceEntityRepository;
    private final MikrotikMonitoringService mikrotikMonitoringService;
    private final ServerRepository serverRepository;
    private final MikrotikConnectionService mikrotikConnectionService;
    private final MikrotikRouterOsApiClient mikrotikRouterOsApiClient;
    private final MikrotikWanTrafficCollectorService mikrotikWanTrafficCollectorService;

    @GetMapping("/network/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNetworkSummary(
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String location) {
        prepareTrafficDashboardData();
        List<NetworkDevice> networkDevices = filterDevices(networkDeviceRepository.findByIsActiveTrue(), deviceType, location);
        List<MikrotikDevice> mikrotiks = mikrotikRepository.findAll();
        List<DeviceMetrics> latestMetrics = getLatestMetrics(networkDevices);
        List<NetworkAlert> alerts = alertRepository.findAll();

        long onlineDevices = !networkDevices.isEmpty()
                ? networkDevices.stream().filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.ONLINE).count()
                : countLegacyOnline(mikrotiks);
        long offlineDevices = !networkDevices.isEmpty()
                ? networkDevices.stream().filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.OFFLINE).count()
                : countLegacyOffline(mikrotiks);
        long warningDevices = networkDevices.stream()
                .filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.WARNING)
                .count();
        long totalDevices = !networkDevices.isEmpty() ? networkDevices.size() : mikrotiks.size();

        long downloadBps = latestMetrics.stream()
                .map(DeviceMetrics::getTrafficRxBps)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        long uploadBps = latestMetrics.stream()
                .map(DeviceMetrics::getTrafficTxBps)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        long activeInterfaces = countActiveInterfaces(networkDevices);
        long activeAlerts = alerts.stream().filter(alert -> !"closed".equalsIgnoreCase(alert.getStatus())).count();
        long criticalAlerts = alerts.stream()
                .filter(alert -> "critical".equalsIgnoreCase(alert.getSeverity()) && !"closed".equalsIgnoreCase(alert.getStatus()))
                .count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalDownload", downloadBps > 0 ? formatMbps(downloadBps) : EMPTY_DATA_FROM_DATABASE);
        summary.put("totalUpload", uploadBps > 0 ? formatMbps(uploadBps) : EMPTY_DATA_FROM_DATABASE);
        summary.put("downloadUnit", "Mbps");
        summary.put("uploadUnit", "Mbps");
        summary.put("activeInterfaces", activeInterfaces > 0 ? activeInterfaces : EMPTY_DATA_FROM_DATABASE);
        summary.put("networkUptime", totalDevices > 0 ? percentage(onlineDevices, totalDevices) + "%" : EMPTY_DATA_FROM_DATABASE);
        summary.put("devicesOnline", onlineDevices);
        summary.put("devicesOffline", offlineDevices);
        summary.put("warningDevices", warningDevices);
        summary.put("totalDevices", totalDevices);
        summary.put("totalCustomers", customerRepository.count());
        summary.put("activeCustomers", customerRepository.countByStatus("active"));
        summary.put("activeAlerts", activeAlerts);
        summary.put("criticalAlerts", criticalAlerts);
        summary.put("uptime", averageUptime(networkDevices, mikrotiks));

        return databaseResponse("Network summary", summary);
    }

    @GetMapping("/devices/by-type")
    public ResponseEntity<ApiResponse<Map<String, Map<String, Object>>>> getDevicesByType(
            @RequestParam(required = false) String location) {
        prepareTrafficDashboardData();
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        List<NetworkDevice> networkDevices = filterDevices(networkDeviceRepository.findByIsActiveTrue(), null, location);

        if (!networkDevices.isEmpty()) {
            result.put("router", buildNetworkTypeSummary("Router", "router", networkDevices.stream()
                    .filter(this::isRouterType)
                    .toList()));
            result.put("switch", buildNetworkTypeSummary("Switch", "switch", networkDevices.stream()
                    .filter(device -> device.getDeviceType() == NetworkDevice.DeviceType.SWITCH)
                    .toList()));
            result.put("ap", buildNetworkTypeSummary("Access Point", "ap", networkDevices.stream()
                    .filter(device -> device.getDeviceType() == NetworkDevice.DeviceType.AP)
                    .toList()));
        } else {
            result.put("router", buildLegacyMikrotikSummary("Router", "router", mikrotikRepository.findAll()));
            result.put("switch", emptyTypeSummary("Switch", "switch"));
            result.put("ap", emptyTypeSummary("Access Point", "ap"));
        }

        return databaseResponse("Devices by type", result);
    }

    @GetMapping("/interfaces/traffic")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getInterfaceTraffic(
            @RequestParam(required = false, defaultValue = "24") Integer hours,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Long serverId) {
        if (serverId != null) {
            return getServerWanInterfaceTraffic(serverId);
        }

        prepareTrafficDashboardData();
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        Set<Long> allowedDeviceIds = filterDevices(networkDeviceRepository.findByIsActiveTrue(), deviceType, location).stream()
                .map(NetworkDevice::getId)
                .collect(Collectors.toSet());
        if (allowedDeviceIds.isEmpty()) {
            return databaseResponse("Interface traffic", Collections.emptyList());
        }

        Map<String, DeviceMetrics> latestInterfaceMetrics = new LinkedHashMap<>();
        deviceMetricsRepository.findRecentInterfaceMetricsWithDevice(new ArrayList<>(allowedDeviceIds), since).stream()
                .sorted(Comparator.comparing(DeviceMetrics::getTimestamp).reversed())
                .forEach(metric -> latestInterfaceMetrics.putIfAbsent(
                        metric.getDevice().getId() + "::" + metric.getInterfaceName().toLowerCase(Locale.ROOT), metric));

        List<Map<String, Object>> interfaces = latestInterfaceMetrics.values().stream()
                .sorted(Comparator.comparing((DeviceMetrics metric) -> defaultLong(metric.getTrafficRxBps()) + defaultLong(metric.getTrafficTxBps()))
                        .reversed())
                .map(metric -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", metric.getId());
                    item.put("interface", metric.getInterfaceName());
                    item.put("deviceName", metric.getDevice() != null ? metric.getDevice().getDeviceName() : EMPTY_DATA_FROM_DATABASE);
                    item.put("deviceLocation", metric.getDevice() != null ? stringValue(metric.getDevice().getLocation()) : EMPTY_DATA_FROM_DATABASE);
                    item.put("download", toMbps(metric.getTrafficRxBps()));
                    item.put("upload", toMbps(metric.getTrafficTxBps()));
                    item.put("utilization", utilization(metric));
                    item.put("packetErrors", defaultLong(metric.getErrorsRx()) + defaultLong(metric.getErrorsTx()));
                    item.put("status", metric.getInterfaceStatus() != null ? metric.getInterfaceStatus().toUpperCase(Locale.ROOT) : EMPTY_DATA_FROM_DATABASE);
                    return item;
                })
                .limit(50)
                .toList();

        return databaseResponse("Interface traffic", interfaces);
    }

    @GetMapping("/network/quality")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNetworkQuality(
            @RequestParam(required = false, defaultValue = "24") Integer hours,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String location) {
        prepareTrafficDashboardData();
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        Set<Long> allowedDeviceIds = filterDevices(networkDeviceRepository.findByIsActiveTrue(), deviceType, location).stream()
                .map(NetworkDevice::getId)
                .collect(Collectors.toSet());
        List<DeviceMetrics> metrics = allowedDeviceIds.stream()
                .flatMap(deviceId -> deviceMetricsRepository
                        .findSummaryMetricsByDeviceIdAndTimestampBetweenOrderByTimestampAsc(deviceId, since, LocalDateTime.now())
                        .stream())
                .sorted(Comparator.comparing(DeviceMetrics::getTimestamp))
                .toList();

        BigDecimal avgLatency = average(metrics.stream().map(DeviceMetrics::getLatencyMs).toList());
        BigDecimal avgPacketLoss = average(metrics.stream().map(DeviceMetrics::getPacketLoss).toList());
        BigDecimal avgJitter = average(metrics.stream().map(DeviceMetrics::getJitterMs).toList());

        Map<String, Object> quality = new LinkedHashMap<>();
        quality.put("latency", avgLatency != null ? avgLatency : EMPTY_DATA_FROM_DATABASE);
        quality.put("latencyStatus", avgLatency != null && avgLatency.compareTo(BigDecimal.valueOf(50)) > 0 ? "warning" : "good");
        quality.put("packetLoss", avgPacketLoss != null ? avgPacketLoss : EMPTY_DATA_FROM_DATABASE);
        quality.put("packetLossStatus", avgPacketLoss != null && avgPacketLoss.compareTo(BigDecimal.ONE) > 0 ? "warning" : "good");
        quality.put("jitter", avgJitter != null ? avgJitter : EMPTY_DATA_FROM_DATABASE);
        quality.put("jitterStatus", avgJitter != null && avgJitter.compareTo(BigDecimal.valueOf(30)) > 0 ? "warning" : "good");
        quality.put("upstreamPing", avgLatency != null ? avgLatency : EMPTY_DATA_FROM_DATABASE);
        quality.put("upstreamPingStatus", avgLatency != null && avgLatency.compareTo(BigDecimal.valueOf(50)) > 0 ? "warning" : "good");
        quality.put("upstreamGateway", EMPTY_DATA_FROM_DATABASE);
        quality.put("upstreamGatewayName", EMPTY_DATA_FROM_DATABASE);
        quality.put("history", metrics.stream()
                .filter(metric -> metric.getLatencyMs() != null || metric.getPacketLoss() != null)
                .map(metric -> {
                    Map<String, Object> history = new LinkedHashMap<>();
                    history.put("hour", metric.getTimestamp().getHour() + ":00");
                    history.put("latency", metric.getLatencyMs());
                    history.put("packetLoss", metric.getPacketLoss());
                    return history;
                })
                .toList());

        return databaseResponse("Network quality metrics", quality);
    }

    @GetMapping("/users/top-bandwidth")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTopBandwidthUsers(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        prepareTrafficDashboardData();
        List<Map<String, Object>> users = mikrotikMonitoringService.getTopActiveUsers(limit);
        return databaseResponse("Top bandwidth users", users);
    }

    @GetMapping("/traffic/history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTrafficHistory(
            @RequestParam(required = false, defaultValue = "24") Integer hours,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String location) {
        prepareTrafficDashboardData();
        List<NetworkDevice> devices = filterDevices(networkDeviceRepository.findByIsActiveTrue(), deviceType, location);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusHours(hours);

        Map<LocalDateTime, long[]> buckets = new LinkedHashMap<>();
        LocalDateTime cursor = since.withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = now.withMinute(0).withSecond(0).withNano(0);
        while (!cursor.isAfter(end)) {
            buckets.put(cursor, new long[]{0L, 0L});
            cursor = cursor.plusHours(1);
        }

        for (NetworkDevice device : devices) {
            List<DeviceMetrics> metrics = deviceMetricsRepository.findSummaryMetricsByDeviceIdAndTimestampBetweenOrderByTimestampAsc(
                    device.getId(), since, now);
            for (DeviceMetrics metric : metrics) {
                if (metric.getTimestamp() == null) {
                    continue;
                }
                LocalDateTime bucketKey = metric.getTimestamp().withMinute(0).withSecond(0).withNano(0);
                long[] bucket = buckets.computeIfAbsent(bucketKey, ignored -> new long[]{0L, 0L});
                bucket[0] += defaultLong(metric.getTrafficRxBps());
                bucket[1] += defaultLong(metric.getTrafficTxBps());
            }
        }

        List<String> labels = new ArrayList<>();
        List<Double> download = new ArrayList<>();
        List<Double> upload = new ArrayList<>();

        for (Map.Entry<LocalDateTime, long[]> entry : buckets.entrySet()) {
            labels.add(formatHistoryLabel(entry.getKey(), hours));
            download.add(toMbps(entry.getValue()[0]));
            upload.add(toMbps(entry.getValue()[1]));
        }

        double avgDownload = download.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0d);
        double avgUpload = upload.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0d);
        double peakDownload = download.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).max().orElse(0d);
        double peakUpload = upload.stream().filter(Objects::nonNull).mapToDouble(Double::doubleValue).max().orElse(0d);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("labels", labels);
        payload.put("download", download);
        payload.put("upload", upload);
        payload.put("avgDownload", roundDouble(avgDownload));
        payload.put("avgUpload", roundDouble(avgUpload));
        payload.put("peakDownload", roundDouble(peakDownload));
        payload.put("peakUpload", roundDouble(peakUpload));
        return databaseResponse("Traffic history", payload);
    }

    @GetMapping("/olt/monitoring")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getOltMonitoring() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Fitur OLT dinonaktifkan"));
    }

    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllAlerts(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String deviceName,
            @RequestParam(required = false) String alertId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {

        try {
            List<NetworkAlert> alerts;

            if (severity != null || status != null || deviceType != null || location != null || deviceName != null || alertId != null || source != null) {
                alerts = alertRepository.findByFilters(severity, status, deviceType, location, deviceName, alertId, source);
            } else {
                alerts = alertRepository.findAll();
                alerts.sort((a, b) -> {
                    int severityCompare = getSeverityOrder(a.getSeverity()) - getSeverityOrder(b.getSeverity());
                    if (severityCompare != 0) {
                        return severityCompare;
                    }
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                });
            }

            Map<String, Object> result = new HashMap<>();
            result.put("alerts", alerts.stream().limit(limit).toList());
            result.put("stats", getAlertStatsMap());
            result.put("total", alerts.size());
            return databaseResponse("Data alert berhasil diambil", result);
        } catch (Exception ex) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("alerts", Collections.emptyList());
            fallback.put("stats", emptyAlertStatsMap());
            fallback.put("total", 0);
            return databaseResponse("Data alert sementara belum tersedia", fallback);
        }
    }

    @GetMapping("/alerts/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAlertStats() {
        try {
            return databaseResponse("Statistik alert", getAlertStatsMap());
        } catch (Exception ex) {
            return databaseResponse("Statistik alert sementara belum tersedia", emptyAlertStatsMap());
        }
    }

    private Map<String, Object> getAlertStatsMap() {
        List<NetworkAlert> all = alertRepository.findAll();

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", all.size());
        stats.put("active", all.stream().filter(a -> "active".equals(a.getStatus())).count());
        stats.put("acknowledged", all.stream().filter(a -> "acknowledged".equals(a.getStatus())).count());
        stats.put("investigating", all.stream().filter(a -> "investigating".equals(a.getStatus())).count());
        stats.put("resolved", all.stream().filter(a -> "resolved".equals(a.getStatus())).count());
        stats.put("closed", all.stream().filter(a -> "closed".equals(a.getStatus())).count());
        stats.put("critical", all.stream().filter(a -> "critical".equals(a.getSeverity())).count());
        stats.put("major", all.stream().filter(a -> "major".equals(a.getSeverity())).count());
        stats.put("warning", all.stream().filter(a -> "warning".equals(a.getSeverity())).count());
        stats.put("info", all.stream().filter(a -> "info".equals(a.getSeverity())).count());
        stats.put("unacknowledged", all.stream()
                .filter(a -> "active".equals(a.getStatus()) || "acknowledged".equals(a.getStatus()) || "investigating".equals(a.getStatus()))
                .count());
        return stats;
    }

    private Map<String, Object> emptyAlertStatsMap() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", 0L);
        stats.put("active", 0L);
        stats.put("acknowledged", 0L);
        stats.put("investigating", 0L);
        stats.put("resolved", 0L);
        stats.put("closed", 0L);
        stats.put("critical", 0L);
        stats.put("major", 0L);
        stats.put("warning", 0L);
        stats.put("info", 0L);
        stats.put("unacknowledged", 0L);
        return stats;
    }

    @GetMapping("/alerts/active")
    public ResponseEntity<ApiResponse<List<NetworkAlert>>> getActiveAlerts() {
        return databaseResponse("Active alerts", alertRepository.findActiveAlerts());
    }

    @GetMapping("/alerts/{id}")
    public ResponseEntity<ApiResponse<NetworkAlert>> getAlert(@PathVariable Long id) {
        return alertRepository.findById(id)
                .map(alert -> ResponseEntity.ok(ApiResponse.success("Alert ditemukan", alert)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Alert tidak ditemukan")));
    }

    @PostMapping("/alerts")
    public ResponseEntity<ApiResponse<NetworkAlert>> createAlert(@RequestBody NetworkAlert alert) {
        return ResponseEntity.ok(ApiResponse.success("Alert berhasil dibuat", alertRepository.save(alert)));
    }

    @PutMapping("/alerts/{id}")
    public ResponseEntity<ApiResponse<NetworkAlert>> updateAlert(@PathVariable Long id, @RequestBody NetworkAlert alertUpdate) {
        return alertRepository.findById(id)
                .map(alert -> {
                    if (alertUpdate.getSeverity() != null) alert.setSeverity(alertUpdate.getSeverity());
                    if (alertUpdate.getMessage() != null) alert.setMessage(alertUpdate.getMessage());
                    if (alertUpdate.getMetricValue() != null) alert.setMetricValue(alertUpdate.getMetricValue());
                    if (alertUpdate.getThreshold() != null) alert.setThreshold(alertUpdate.getThreshold());
                    if (alertUpdate.getAssignedEngineer() != null) alert.setAssignedEngineer(alertUpdate.getAssignedEngineer());
                    if (alertUpdate.getAffectedCustomers() != null) alert.setAffectedCustomers(alertUpdate.getAffectedCustomers());
                    if (alertUpdate.getAffectedService() != null) alert.setAffectedService(alertUpdate.getAffectedService());
                    if (alertUpdate.getSlaImpact() != null) alert.setSlaImpact(alertUpdate.getSlaImpact());
                    return ResponseEntity.ok(ApiResponse.success("Alert berhasil diupdate", alertRepository.save(alert)));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Alert tidak ditemukan")));
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public ResponseEntity<ApiResponse<NetworkAlert>> acknowledgeAlert(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        return alertRepository.findById(id)
                .map(alert -> {
                    alert.setAcknowledged(true);
                    alert.setAcknowledgedAt(LocalDateTime.now());
                    alert.setStatus("acknowledged");
                    if (payload.containsKey("notes")) alert.setAcknowledgedNotes(payload.get("notes"));
                    if (payload.containsKey("engineer")) alert.setAssignedEngineer(payload.get("engineer"));
                    return ResponseEntity.ok(ApiResponse.success("Alert berhasil diacknowledge", alertRepository.save(alert)));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Alert tidak ditemukan")));
    }

    @PostMapping("/alerts/{id}/investigate")
    public ResponseEntity<ApiResponse<NetworkAlert>> startInvestigating(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        return alertRepository.findById(id)
                .map(alert -> {
                    alert.setStatus("investigating");
                    if (payload.containsKey("notes")) alert.addInvestigationNote(payload.get("notes"));
                    return ResponseEntity.ok(ApiResponse.success("Investigasi dimulai", alertRepository.save(alert)));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Alert tidak ditemukan")));
    }

    @PostMapping("/alerts/{id}/resolve")
    public ResponseEntity<ApiResponse<NetworkAlert>> resolveAlert(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        return alertRepository.findById(id)
                .map(alert -> {
                    alert.setResolved(true);
                    alert.setResolvedAt(LocalDateTime.now());
                    alert.setStatus("resolved");
                    alert.setSeverity("resolved");
                    if (payload.containsKey("notes")) alert.setResolvedNotes(payload.get("notes"));
                    return ResponseEntity.ok(ApiResponse.success("Alert berhasil diresolve", alertRepository.save(alert)));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Alert tidak ditemukan")));
    }

    @PostMapping("/alerts/{id}/close")
    public ResponseEntity<ApiResponse<NetworkAlert>> closeAlert(@PathVariable Long id) {
        return alertRepository.findById(id)
                .map(alert -> {
                    alert.setClosed(true);
                    alert.setClosedAt(LocalDateTime.now());
                    alert.setStatus("closed");
                    return ResponseEntity.ok(ApiResponse.success("Alert berhasil ditutup", alertRepository.save(alert)));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Alert tidak ditemukan")));
    }

    @PostMapping("/alerts/{id}/note")
    public ResponseEntity<ApiResponse<NetworkAlert>> addNote(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) {
        return alertRepository.findById(id)
                .map(alert -> {
                    if (payload.containsKey("note")) alert.addInvestigationNote(payload.get("note"));
                    return ResponseEntity.ok(ApiResponse.success("Note ditambahkan", alertRepository.save(alert)));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Alert tidak ditemukan")));
    }

    @DeleteMapping("/alerts/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAlert(@PathVariable Long id) {
        if (!alertRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Alert tidak ditemukan"));
        }
        alertRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Alert berhasil dihapus", null));
    }

    @PostMapping("/alerts/bulk/acknowledge")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkAcknowledge(@RequestBody Map<String, Object> payload) {
        List<Long> ids = (List<Long>) payload.get("ids");
        String notes = (String) payload.getOrDefault("notes", "Bulk acknowledge");

        int count = 0;
        for (Long id : ids) {
            Optional<NetworkAlert> alert = alertRepository.findById(id);
            if (alert.isPresent()) {
                NetworkAlert value = alert.get();
                value.setAcknowledged(true);
                value.setAcknowledgedAt(LocalDateTime.now());
                value.setStatus("acknowledged");
                value.setAcknowledgedNotes(notes);
                alertRepository.save(value);
                count++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("acknowledged", count);
        result.put("total", ids.size());
        return ResponseEntity.ok(ApiResponse.success("Bulk acknowledge berhasil", result));
    }

    @PostMapping("/alerts/bulk/resolve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkResolve(@RequestBody Map<String, Object> payload) {
        List<Long> ids = (List<Long>) payload.get("ids");
        String notes = (String) payload.getOrDefault("notes", "Bulk resolve");

        int count = 0;
        for (Long id : ids) {
            Optional<NetworkAlert> alert = alertRepository.findById(id);
            if (alert.isPresent()) {
                NetworkAlert value = alert.get();
                value.setResolved(true);
                value.setResolvedAt(LocalDateTime.now());
                value.setStatus("resolved");
                value.setSeverity("resolved");
                value.setResolvedNotes(notes);
                alertRepository.save(value);
                count++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("resolved", count);
        result.put("total", ids.size());
        return ResponseEntity.ok(ApiResponse.success("Bulk resolve berhasil", result));
    }

    @GetMapping("/alerts/export")
    public ResponseEntity<ApiResponse<List<NetworkAlert>>> exportAlerts(
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String location) {
        return databaseResponse("Alerts untuk export",
                alertRepository.findByFilters(severity, status, null, location, null, null, null));
    }

    @GetMapping("/alerts/analytics/devices")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getTopProblemDevices(
            @RequestParam(required = false, defaultValue = "24") Integer hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<Map<String, Object>> devices = alertRepository.findTopProblemDevices(since, PageRequest.of(0, 10)).stream()
                .map(row -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("deviceName", row[0]);
                    item.put("count", row[1]);
                    return item;
                })
                .toList();
        return databaseResponse("Top problem devices", devices);
    }

    @GetMapping("/alerts/analytics/frequent")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMostFrequentAlerts(
            @RequestParam(required = false, defaultValue = "24") Integer hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<Map<String, Object>> alerts = alertRepository.findMostFrequentAlerts(since, PageRequest.of(0, 10)).stream()
                .map(row -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("alertType", row[0]);
                    item.put("count", row[1]);
                    return item;
                })
                .toList();
        return databaseResponse("Most frequent alerts", alerts);
    }

    @GetMapping("/alerts/analytics/trend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAlertTrend(
            @RequestParam(required = false, defaultValue = "24") Integer hours) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusHours(hours);
        List<NetworkAlert> alerts = alertRepository.findAlertsAfter(since);

        Map<String, Integer> hourlyCounts = new LinkedHashMap<>();
        for (int i = hours - 1; i >= 0; i--) {
            LocalDateTime hourStart = now.minusHours(i);
            String hourKey = hourStart.getHour() + ":00";
            int hour = hourStart.getHour();
            hourlyCounts.put(hourKey, (int) alerts.stream()
                    .filter(alert -> alert.getCreatedAt().getHour() == hour)
                    .count());
        }

        Map<String, Object> trend = new HashMap<>();
        trend.put("labels", new ArrayList<>(hourlyCounts.keySet()));
        trend.put("data", new ArrayList<>(hourlyCounts.values()));
        trend.put("severityBreakdown", Map.of(
                "critical", (int) alerts.stream().filter(a -> "critical".equals(a.getSeverity())).count(),
                "major", (int) alerts.stream().filter(a -> "major".equals(a.getSeverity())).count(),
                "warning", (int) alerts.stream().filter(a -> "warning".equals(a.getSeverity())).count(),
                "info", (int) alerts.stream().filter(a -> "info".equals(a.getSeverity())).count()
        ));

        return databaseResponse("Alert trend", trend);
    }

    @GetMapping("/alerts/analytics/mttr")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMTTR(
            @RequestParam(required = false, defaultValue = "168") Integer hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<NetworkAlert> resolvedAlerts = alertRepository.findAll().stream()
                .filter(alert -> alert.getResolvedAt() != null && alert.getCreatedAt().isAfter(since))
                .toList();

        long resolvedCount = resolvedAlerts.stream()
                .map(NetworkAlert::getResolutionTimeMinutes)
                .filter(Objects::nonNull)
                .count();
        double averageMinutes = resolvedAlerts.stream()
                .map(NetworkAlert::getResolutionTimeMinutes)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0d);

        Map<String, Object> mttr = new HashMap<>();
        mttr.put("averageMinutes", averageMinutes);
        mttr.put("averageHours", averageMinutes / 60);
        mttr.put("resolvedCount", resolvedCount);
        return databaseResponse("MTTR Analytics", mttr);
    }

    @GetMapping("/alerts/grouped/location")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAlertsGroupedByLocation() {
        List<Map<String, Object>> grouped = alertRepository.countByLocation().stream()
                .map(row -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("location", row[0]);
                    item.put("count", row[1]);
                    return item;
                })
                .toList();
        return databaseResponse("Alerts by location", grouped);
    }

    @GetMapping("/alerts/grouped/device-type")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAlertsGroupedByDeviceType() {
        List<Map<String, Object>> grouped = alertRepository.countByDeviceType().stream()
                .map(row -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("deviceType", row[0]);
                    item.put("count", row[1]);
                    return item;
                })
                .toList();
        return databaseResponse("Alerts by device type", grouped);
    }

    @GetMapping("/alerts/device/{deviceId}")
    public ResponseEntity<ApiResponse<List<NetworkAlert>>> getAlertsByDevice(@PathVariable Long deviceId) {
        return databaseResponse("Related alerts", alertRepository.findRelatedAlertsByDevice(deviceId));
    }

    @GetMapping("/alerts/recent")
    public ResponseEntity<ApiResponse<List<NetworkAlert>>> getRecentAlerts(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return databaseResponse("Recent alerts", alertRepository.findRecentAlerts(limit));
    }

    @GetMapping("/alerts/filters/locations")
    public ResponseEntity<ApiResponse<List<String>>> getAlertLocations() {
        List<String> locations = alertRepository.countByLocation().stream()
                .map(row -> row[0] != null ? row[0].toString() : null)
                .filter(Objects::nonNull)
                .toList();
        return databaseResponse("Locations", locations);
    }

    @GetMapping("/alerts/filters/device-types")
    public ResponseEntity<ApiResponse<List<String>>> getAlertDeviceTypes() {
        List<String> types = alertRepository.countByDeviceType().stream()
                .map(row -> row[0] != null ? row[0].toString() : null)
                .filter(Objects::nonNull)
                .toList();
        return databaseResponse("Device types", types);
    }

    @GetMapping("/devices/status")
    public ResponseEntity<ApiResponse<Object>> getDeviceStatuses() {
        List<MikrotikDevice> mikrotiks = mikrotikRepository.findAll();

        return databaseResponse("Device status", Map.of(
                "mikrotik", Map.of(
                        "total", mikrotiks.size(),
                        "online", countLegacyOnline(mikrotiks),
                        "offline", countLegacyOffline(mikrotiks)
                )
        ));
    }

    @GetMapping("/traffic/summary")
    public ResponseEntity<ApiResponse<Object>> getTrafficSummary() {
        List<DeviceMetrics> latestMetrics = getLatestMetrics(networkDeviceRepository.findByIsActiveTrue());
        long totalBandwidth = latestMetrics.stream()
                .mapToLong(metric -> defaultLong(metric.getTrafficRxBps()) + defaultLong(metric.getTrafficTxBps()))
                .sum();
        long peakUsage = latestMetrics.stream()
                .mapToLong(metric -> Math.max(defaultLong(metric.getTrafficRxBps()), defaultLong(metric.getTrafficTxBps())))
                .max()
                .orElse(0L);
        long averageUsage = latestMetrics.isEmpty() ? 0L : totalBandwidth / latestMetrics.size();
        int activeConnections = latestMetrics.stream()
                .mapToInt(metric -> defaultInteger(metric.getActiveSessions())
                        + defaultInteger(metric.getActivePppoe())
                        + defaultInteger(metric.getActiveHotspot()))
                .sum();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalBandwidth", totalBandwidth > 0 ? formatBandwidth(totalBandwidth) : EMPTY_DATA_FROM_DATABASE);
        summary.put("peakUsage", peakUsage > 0 ? formatBandwidth(peakUsage) : EMPTY_DATA_FROM_DATABASE);
        summary.put("averageUsage", averageUsage > 0 ? formatBandwidth(averageUsage) : EMPTY_DATA_FROM_DATABASE);
        summary.put("activeConnections", activeConnections > 0 ? activeConnections : EMPTY_DATA_FROM_DATABASE);
        return databaseResponse("Traffic summary", summary);
    }

    @GetMapping("/network/capacity")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNetworkCapacity(
            @RequestParam(required = false) String deviceType,
            @RequestParam(required = false) String location) {
        prepareTrafficDashboardData();
        List<NetworkDevice> devices = filterDevices(networkDeviceRepository.findByIsActiveTrue(), deviceType, location);
        List<DeviceMetrics> latestMetrics = getLatestMetrics(devices);

        long totalCapacity = latestMetrics.stream()
                .map(DeviceMetrics::getInterfaceSpeed)
                .filter(Objects::nonNull)
                .filter(speed -> speed > 0)
                .mapToLong(Integer::longValue)
                .sum();
        long currentUsage = latestMetrics.stream()
                .mapToLong(metric -> defaultLong(metric.getTrafficRxBps()) + defaultLong(metric.getTrafficTxBps()))
                .sum();
        long peakUsage = latestMetrics.stream()
                .mapToLong(metric -> Math.max(defaultLong(metric.getTrafficRxBps()), defaultLong(metric.getTrafficTxBps())))
                .max()
                .orElse(0L);
        long availableCapacity = Math.max(totalCapacity - currentUsage, 0L);
        int utilization = totalCapacity > 0
                ? BigDecimal.valueOf(currentUsage * 100.0d / totalCapacity).setScale(0, RoundingMode.HALF_UP).intValue()
                : 0;

        Map<String, Object> capacity = new LinkedHashMap<>();
        capacity.put("totalCapacity", totalCapacity > 0 ? formatBandwidth(totalCapacity) : EMPTY_DATA_FROM_DATABASE);
        capacity.put("currentUsage", currentUsage > 0 ? formatBandwidth(currentUsage) : EMPTY_DATA_FROM_DATABASE);
        capacity.put("availableCapacity", availableCapacity > 0 ? formatBandwidth(availableCapacity) : EMPTY_DATA_FROM_DATABASE);
        capacity.put("peakUsage", peakUsage > 0 ? formatBandwidth(peakUsage) : EMPTY_DATA_FROM_DATABASE);
        capacity.put("utilization", utilization);
        return databaseResponse("Network capacity", capacity);
    }

    @GetMapping("/servers")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getServers() {
        List<Map<String, Object>> servers = serverRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(this::buildServerOption)
                .toList();
        return databaseResponse("Servers", servers);
    }

    @GetMapping("/traffic/realtime")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRealtimeServerTraffic(@RequestParam Long serverId) {
        try {
            return databaseResponse("Realtime WAN traffic", mikrotikWanTrafficCollectorService.getRealtimePayload(serverId));
        } catch (NoSuchElementException ex) {
            return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/traffic/status")
    public ResponseEntity<ApiResponse<Object>> getRealtimeTrafficStatus(@RequestParam(required = false) Long serverId) {
        try {
            Object payload = serverId != null
                    ? mikrotikWanTrafficCollectorService.getStatusPayload(serverId)
                    : mikrotikWanTrafficCollectorService.getStatusPayloads();
            return databaseResponse("Realtime WAN status", payload);
        } catch (NoSuchElementException ex) {
            return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/locations")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLocations() {
        prepareTrafficDashboardData();
        Map<String, Map<String, Object>> locationMap = new LinkedHashMap<>();

        regionRepository.findAll().forEach(region -> {
            if (region.getName() == null || region.getName().isBlank()) {
                return;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("id", region.getId());
            item.put("name", region.getName());
            item.put("code", region.getCode());
            locationMap.put(region.getName().trim().toLowerCase(Locale.ROOT), item);
        });

        networkDeviceRepository.findDistinctLocations().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .forEach(name -> locationMap.putIfAbsent(name.toLowerCase(Locale.ROOT), buildLocationItem(name)));

        mikrotikRepository.findByIsActiveTrue().stream()
                .map(MikrotikDevice::getLocation)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .forEach(name -> locationMap.putIfAbsent(name.toLowerCase(Locale.ROOT), buildLocationItem(name)));

        List<Map<String, Object>> locations = locationMap.values().stream()
                .sorted(Comparator.comparing(item -> String.valueOf(item.get("name")), String.CASE_INSENSITIVE_ORDER))
                .toList();
        return databaseResponse("Locations", locations);
    }

    private ResponseEntity<ApiResponse<List<Map<String, Object>>>> getServerWanInterfaceTraffic(Long serverId) {
        try {
            return databaseResponse("Interface traffic", List.of(mikrotikWanTrafficCollectorService.getInterfaceRow(serverId)));
        } catch (NoSuchElementException ex) {
            return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private int getSeverityOrder(String severity) {
        return switch (severity) {
            case "critical" -> 1;
            case "major" -> 2;
            case "warning" -> 3;
            case "info" -> 4;
            case "resolved" -> 5;
            default -> 6;
        };
    }

    private List<DeviceMetrics> getLatestMetrics(List<NetworkDevice> devices) {
        if (devices.isEmpty()) {
            return Collections.emptyList();
        }
        return deviceMetricsRepository.findLatestSummaryByDeviceIds(devices.stream().map(NetworkDevice::getId).toList());
    }

    private long countActiveInterfaces(List<NetworkDevice> devices) {
        if (devices.isEmpty()) {
            return 0L;
        }

        LocalDateTime since = LocalDateTime.now().minusMinutes(10);
        Map<String, DeviceMetrics> latestInterfaceMetrics = new LinkedHashMap<>();
        deviceMetricsRepository.findAll().stream()
                .filter(metric -> metric.getDevice() != null)
                .filter(metric -> devices.stream().anyMatch(device -> Objects.equals(device.getId(), metric.getDevice().getId())))
                .filter(metric -> metric.getTimestamp() != null && metric.getTimestamp().isAfter(since))
                .filter(metric -> metric.getInterfaceName() != null && !metric.getInterfaceName().isBlank())
                .sorted(Comparator.comparing(DeviceMetrics::getTimestamp).reversed())
                .forEach(metric -> latestInterfaceMetrics.putIfAbsent(
                        metric.getDevice().getId() + "::" + metric.getInterfaceName().toLowerCase(Locale.ROOT), metric));

        return latestInterfaceMetrics.values().stream()
                .filter(metric -> "up".equalsIgnoreCase(metric.getInterfaceStatus()))
                .count();
    }

    private void prepareTrafficDashboardData() {
        mikrotikMonitoringService.refreshMonitoringSnapshotIfNeeded();
    }

    private Map<String, Object> buildLocationItem(String name) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", null);
        item.put("name", name);
        item.put("code", null);
        return item;
    }

    private Map<String, Object> buildServerOption(Server server) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", server.getId());
        item.put("name", stringValue(server.getName()));
        item.put("location", normalize(server.getLocation()));
        item.put("mikrotikId", server.getMikrotikId());
        item.put("region", server.getRegion() != null ? normalize(server.getRegion().getName()) : null);
        return item;
    }

    private RealtimeTarget resolveRealtimeTarget(MikrotikDevice mikrotik) {
        List<MikrotikConnectionService.ResolvedTarget> candidates = mikrotikConnectionService.resolveApiCandidates(
                mikrotik.getMonitoringTarget(),
                mikrotik.getApiIpAddress(),
                mikrotik.getWinboxIpAddress(),
                mikrotik.resolveVpnEndpoint(),
                mikrotik.getIpAddress()
        );
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Alamat API Mikrotik untuk server ini belum valid");
        }
        MikrotikConnectionService.ResolvedTarget candidate = candidates.getFirst();
        return new RealtimeTarget(candidate.target(), candidate.source());
    }

    private LiveTrafficResult collectRealtimeTraffic(MikrotikDevice mikrotik, String preferredInterfaceName) {
        List<MikrotikConnectionService.ResolvedTarget> candidates = mikrotikConnectionService.resolveApiCandidates(
                mikrotik.getMonitoringTarget(),
                mikrotik.getApiIpAddress(),
                mikrotik.getWinboxIpAddress(),
                mikrotik.resolveVpnEndpoint(),
                mikrotik.getIpAddress()
        );
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Alamat API Mikrotik untuk server ini belum valid");
        }

        IllegalStateException lastFailure = null;
        for (MikrotikConnectionService.ResolvedTarget candidate : candidates) {
            try {
                MikrotikRouterOsApiClient.MikrotikLiveTrafficSnapshot snapshot = mikrotikRouterOsApiClient.collectWanTraffic(
                        candidate.target(),
                        mikrotik.getUsername(),
                        mikrotik.getPassword(),
                        preferredInterfaceName
                );
                return new LiveTrafficResult(new RealtimeTarget(candidate.target(), candidate.source()), snapshot);
            } catch (IllegalStateException ex) {
                lastFailure = ex;
            }
        }

        throw lastFailure != null ? lastFailure : new IllegalStateException("Gagal mengambil trafik realtime MikroTik");
    }

    private ServerMonitoringContext resolveServerMonitoringContext(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .filter(item -> !Boolean.FALSE.equals(item.getIsActive()))
                .orElse(null);
        if (server == null) {
            return ServerMonitoringContext.error(HttpStatus.NOT_FOUND, "Server tidak ditemukan");
        }
        if (server.getMikrotikId() == null) {
            return ServerMonitoringContext.error(HttpStatus.BAD_REQUEST, "Server belum terhubung ke Mikrotik");
        }

        MikrotikDevice mikrotik = mikrotikRepository.findById(server.getMikrotikId()).orElse(null);
        if (mikrotik == null) {
            return ServerMonitoringContext.error(HttpStatus.NOT_FOUND, "Mikrotik untuk server ini tidak ditemukan");
        }
        if (!hasText(mikrotik.getUsername()) || !hasText(mikrotik.getPassword())) {
            return ServerMonitoringContext.error(HttpStatus.BAD_REQUEST, "Username atau password API Mikrotik belum diisi");
        }

        return ServerMonitoringContext.success(server, mikrotik, resolveNetworkDevice(mikrotik));
    }

    private NetworkDevice resolveNetworkDevice(MikrotikDevice mikrotik) {
        String mikrotikHost = mikrotikConnectionService.extractHost(firstText(
                mikrotik.getApiIpAddress(),
                mikrotik.resolveVpnHost(),
                mikrotik.getIpAddress(),
                mikrotik.getWinboxIpAddress()
        ));

        return networkDeviceRepository.findAll().stream()
                .filter(device -> Boolean.TRUE.equals(device.getIsActive()))
                .filter(device -> device.getDeviceType() == NetworkDevice.DeviceType.MIKROTIK)
                .filter(device -> Objects.equals(device.getSourceId(), mikrotik.getId())
                        || Objects.equals(normalize(device.getIpAddress()), normalize(mikrotikHost))
                        || Objects.equals(normalize(device.getDeviceName()), normalize(mikrotik.getName())))
                .max(Comparator.comparing(NetworkDevice::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private Map<String, Object> buildRealtimeTrafficPayload(Server server,
                                                            MikrotikDevice mikrotik,
                                                            RealtimeTarget realtimeTarget,
                                                            MikrotikRouterOsApiClient.MikrotikLiveTrafficSnapshot snapshot,
                                                            boolean fallback,
                                                            String fallbackReason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serverId", server.getId());
        payload.put("serverName", stringValue(server.getName()));
        payload.put("serverLocation", stringValue(server.getLocation()));
        payload.put("mikrotikId", mikrotik.getId());
        payload.put("mikrotikName", stringValue(firstText(mikrotik.getName(), snapshot.deviceName())));
        payload.put("targetHost", realtimeTarget.target().host());
        payload.put("targetPort", realtimeTarget.target().port());
        payload.put("targetSource", realtimeTarget.source());
        payload.put("monitoringTarget", stringValue(firstText(mikrotik.getMonitoringTarget(), "vpn")));
        payload.put("interfaceName", stringValue(snapshot.interfaceName()));
        payload.put("interfaceComment", stringValue(snapshot.interfaceComment()));
        payload.put("status", stringValue(snapshot.status()));
        payload.put("download", toMbps(snapshot.rxBps()));
        payload.put("upload", toMbps(snapshot.txBps()));
        payload.put("downloadBps", snapshot.rxBps());
        payload.put("uploadBps", snapshot.txBps());
        payload.put("rxPackets", snapshot.rxPackets());
        payload.put("txPackets", snapshot.txPackets());
        payload.put("rxErrors", snapshot.rxErrors());
        payload.put("txErrors", snapshot.txErrors());
        payload.put("speed", snapshot.speed() > 0 ? formatBandwidth(snapshot.speed()) : EMPTY_DATA_FROM_DATABASE);
        payload.put("timestamp", snapshot.collectedAt());
        payload.put("fallback", fallback);
        payload.put("fallbackReason", fallbackReason);
        return payload;
    }

    private Optional<Map<String, Object>> buildCachedTrafficPayload(ServerMonitoringContext context, String fallbackReason) {
        Optional<DeviceMetrics> latestWanMetric = findLatestWanMetric(context.networkDevice());
        if (latestWanMetric.isEmpty()) {
            return Optional.empty();
        }

        DeviceMetrics metric = latestWanMetric.get();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serverId", context.server().getId());
        payload.put("serverName", stringValue(context.server().getName()));
        payload.put("serverLocation", stringValue(context.server().getLocation()));
        payload.put("mikrotikId", context.mikrotik().getId());
        payload.put("mikrotikName", stringValue(context.mikrotik().getName()));
        payload.put("targetHost", EMPTY_DATA_FROM_DATABASE);
        payload.put("targetPort", EMPTY_DATA_FROM_DATABASE);
        payload.put("targetSource", "database");
        payload.put("monitoringTarget", stringValue(firstText(context.mikrotik().getMonitoringTarget(), "vpn")));
        payload.put("interfaceName", stringValue(metric.getInterfaceName()));
        payload.put("interfaceComment", EMPTY_DATA_FROM_DATABASE);
        payload.put("status", stringValue(metric.getInterfaceStatus()));
        payload.put("download", toMbps(metric.getTrafficRxBps()));
        payload.put("upload", toMbps(metric.getTrafficTxBps()));
        payload.put("downloadBps", defaultLong(metric.getTrafficRxBps()));
        payload.put("uploadBps", defaultLong(metric.getTrafficTxBps()));
        payload.put("rxPackets", defaultLong(metric.getPacketsRx()));
        payload.put("txPackets", defaultLong(metric.getPacketsTx()));
        payload.put("rxErrors", defaultLong(metric.getErrorsRx()));
        payload.put("txErrors", defaultLong(metric.getErrorsTx()));
        payload.put("speed", metric.getInterfaceSpeed() != null && metric.getInterfaceSpeed() > 0
                ? formatBandwidth(metric.getInterfaceSpeed())
                : EMPTY_DATA_FROM_DATABASE);
        payload.put("timestamp", metric.getTimestamp());
        payload.put("fallback", true);
        payload.put("fallbackReason", fallbackReason);
        return Optional.of(payload);
    }

    private Optional<DeviceMetrics> findLatestWanMetric(NetworkDevice device) {
        if (device == null || device.getId() == null) {
            return Optional.empty();
        }

        return deviceMetricsRepository.findLatestMetrics(device.getId(), PageRequest.of(0, 50)).stream()
                .filter(metric -> hasText(metric.getInterfaceName()))
                .filter(metric -> isWanInterface(metric.getInterfaceName()) || isEthernetInterface(metric.getInterfaceName()))
                .sorted(Comparator
                        .comparing((DeviceMetrics metric) -> isWanInterface(metric.getInterfaceName()) ? 1 : 0)
                        .reversed()
                        .thenComparing(DeviceMetrics::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .findFirst();
    }

    private String resolvePreferredWanInterfaceName(ServerMonitoringContext context) {
        return findLatestWanMetric(context.networkDevice())
                .map(DeviceMetrics::getInterfaceName)
                .filter(this::hasText)
                .orElse(null);
    }

    private Map<String, Object> buildRealtimeInterfaceRow(ServerMonitoringContext context,
                                                          MikrotikRouterOsApiClient.MikrotikLiveTrafficSnapshot snapshot,
                                                          boolean fallback,
                                                          String fallbackReason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", context.server().getId());
        item.put("interface", snapshot.interfaceName());
        item.put("deviceName", stringValue(context.mikrotik().getName()));
        item.put("deviceLocation", stringValue(context.server().getLocation()));
        item.put("download", toMbps(snapshot.rxBps()));
        item.put("upload", toMbps(snapshot.txBps()));
        item.put("utilization", snapshot.speed() > 0
                ? BigDecimal.valueOf((snapshot.rxBps() + snapshot.txBps()) * 100.0d / snapshot.speed()).setScale(0, RoundingMode.HALF_UP).intValue()
                : EMPTY_DATA_FROM_DATABASE);
        item.put("packetErrors", snapshot.rxErrors() + snapshot.txErrors());
        item.put("status", snapshot.status());
        item.put("interfaceComment", stringValue(snapshot.interfaceComment()));
        item.put("realtime", true);
        item.put("fallback", fallback);
        item.put("fallbackReason", fallbackReason);
        return item;
    }

    private Map<String, Object> buildCachedInterfaceRow(ServerMonitoringContext context, DeviceMetrics metric, String fallbackReason) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", metric.getId());
        item.put("interface", stringValue(metric.getInterfaceName()));
        item.put("deviceName", stringValue(context.mikrotik().getName()));
        item.put("deviceLocation", stringValue(context.server().getLocation()));
        item.put("download", toMbps(metric.getTrafficRxBps()));
        item.put("upload", toMbps(metric.getTrafficTxBps()));
        item.put("utilization", utilization(metric));
        item.put("packetErrors", defaultLong(metric.getErrorsRx()) + defaultLong(metric.getErrorsTx()));
        item.put("status", stringValue(metric.getInterfaceStatus()));
        item.put("interfaceComment", EMPTY_DATA_FROM_DATABASE);
        item.put("realtime", false);
        item.put("fallback", true);
        item.put("fallbackReason", fallbackReason);
        return item;
    }

    private boolean isWanInterface(String interfaceName) {
        String normalized = normalize(interfaceName);
        return normalized != null && normalized.toLowerCase(Locale.ROOT).contains("wan");
    }

    private boolean isEthernetInterface(String interfaceName) {
        String normalized = normalize(interfaceName);
        return normalized != null && normalized.toLowerCase(Locale.ROOT).startsWith("ether");
    }

    private List<NetworkDevice> filterDevices(List<NetworkDevice> devices, String deviceType, String location) {
        return devices.stream()
                .filter(device -> matchesDeviceType(device, deviceType))
                .filter(device -> location == null || location.isBlank()
                        || (device.getLocation() != null && device.getLocation().equalsIgnoreCase(location)))
                .toList();
    }

    private boolean matchesDeviceType(NetworkDevice device, String deviceType) {
        if (deviceType == null || deviceType.isBlank()) {
            return true;
        }
        String normalized = deviceType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mikrotik", "router" -> isRouterType(device) || device.getDeviceType() == NetworkDevice.DeviceType.MIKROTIK;
            case "switch" -> device.getDeviceType() == NetworkDevice.DeviceType.SWITCH;
            case "ap" -> device.getDeviceType() == NetworkDevice.DeviceType.AP;
            case "olt" -> device.getDeviceType() == NetworkDevice.DeviceType.OLT;
            case "server" -> device.getDeviceType() == NetworkDevice.DeviceType.SERVER;
            default -> device.getDeviceType().name().equalsIgnoreCase(deviceType);
        };
    }

    private String formatHistoryLabel(LocalDateTime value, int hours) {
        if (hours <= 24) {
            return String.format("%02d:00", value.getHour());
        }
        return String.format("%02d/%02d %02d:00", value.getDayOfMonth(), value.getMonthValue(), value.getHour());
    }

    private double roundDouble(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private boolean isRouterType(NetworkDevice device) {
        return device.getDeviceType() == NetworkDevice.DeviceType.MIKROTIK
                || device.getDeviceType() == NetworkDevice.DeviceType.CORE_ROUTER
                || device.getDeviceType() == NetworkDevice.DeviceType.FIREWALL
                || device.getDeviceType() == NetworkDevice.DeviceType.LOAD_BALANCER;
    }

    private Map<String, Object> buildNetworkTypeSummary(String name, String type, List<NetworkDevice> devices) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", name);
        summary.put("type", type);
        summary.put("deviceName", devices.isEmpty() ? EMPTY_DATA_FROM_DATABASE : devices.get(0).getDeviceName());
        summary.put("location", devices.isEmpty() ? EMPTY_DATA_FROM_DATABASE : stringValue(devices.get(0).getLocation()));
        summary.put("status", devices.stream().anyMatch(device -> device.getStatus() == NetworkDevice.DeviceStatus.WARNING) ? "warning"
                : devices.stream().anyMatch(device -> device.getStatus() == NetworkDevice.DeviceStatus.ONLINE) ? "online"
                : "offline");
        summary.put("online", devices.stream().filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.ONLINE).count());
        summary.put("offline", devices.stream().filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.OFFLINE).count());
        summary.put("total", devices.size());
        return summary;
    }

    private Map<String, Object> buildLegacyMikrotikSummary(String name, String type, List<MikrotikDevice> devices) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", name);
        summary.put("type", type);
        summary.put("deviceName", devices.isEmpty() ? EMPTY_DATA_FROM_DATABASE : devices.get(0).getName());
        summary.put("location", devices.isEmpty() ? EMPTY_DATA_FROM_DATABASE : stringValue(devices.get(0).getLocation()));
        summary.put("status", devices.stream().anyMatch(device -> "maintenance".equalsIgnoreCase(device.getStatus())) ? "warning"
                : devices.stream().anyMatch(device -> "online".equalsIgnoreCase(device.getStatus())) ? "online"
                : "offline");
        summary.put("online", countLegacyOnline(devices));
        summary.put("offline", countLegacyOffline(devices));
        summary.put("total", devices.size());
        return summary;
    }

    private Map<String, Object> buildLegacyOltSummary(String name, String type, List<OltDevice> devices) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", name);
        summary.put("type", type);
        summary.put("deviceName", devices.isEmpty() ? EMPTY_DATA_FROM_DATABASE : devices.get(0).getName());
        summary.put("location", devices.isEmpty() ? EMPTY_DATA_FROM_DATABASE : stringValue(devices.get(0).getLocation()));
        summary.put("status", devices.stream().anyMatch(device -> "maintenance".equalsIgnoreCase(device.getStatus())) ? "warning"
                : devices.stream().anyMatch(device -> "online".equalsIgnoreCase(device.getStatus())) ? "online"
                : "offline");
        summary.put("online", countLegacyOnlineOlt(devices));
        summary.put("offline", countLegacyOfflineOlt(devices));
        summary.put("total", devices.size());
        return summary;
    }

    private Map<String, Object> emptyTypeSummary(String name, String type) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", name);
        summary.put("type", type);
        summary.put("deviceName", EMPTY_DATA_FROM_DATABASE);
        summary.put("location", EMPTY_DATA_FROM_DATABASE);
        summary.put("status", "offline");
        summary.put("online", 0L);
        summary.put("offline", 0L);
        summary.put("total", 0);
        return summary;
    }

    private long countLegacyOnline(List<MikrotikDevice> devices) {
        return devices.stream().filter(device -> "online".equalsIgnoreCase(device.getStatus())).count();
    }

    private long countLegacyOffline(List<MikrotikDevice> devices) {
        return devices.stream().filter(device -> "offline".equalsIgnoreCase(device.getStatus())).count();
    }

    private long countLegacyOnlineOlt(List<OltDevice> devices) {
        return devices.stream().filter(device -> "online".equalsIgnoreCase(device.getStatus())).count();
    }

    private long countLegacyOfflineOlt(List<OltDevice> devices) {
        return devices.stream().filter(device -> "offline".equalsIgnoreCase(device.getStatus())).count();
    }

    private String averageUptime(List<NetworkDevice> devices, List<MikrotikDevice> mikrotiks) {
        List<Long> uptimes = new ArrayList<>();
        devices.stream().map(NetworkDevice::getUptimeSeconds).filter(Objects::nonNull).forEach(uptimes::add);
        mikrotiks.stream().map(MikrotikDevice::getUptimeSeconds).filter(Objects::nonNull).forEach(uptimes::add);

        if (uptimes.isEmpty()) {
            return EMPTY_DATA_FROM_DATABASE;
        }

        long average = (long) uptimes.stream().mapToLong(Long::longValue).average().orElse(0L);
        long days = average / 86400;
        long hours = (average % 86400) / 3600;
        return days + " days, " + hours + " hours";
    }

    private String formatMbps(long bps) {
        return BigDecimal.valueOf(bps)
                .divide(BigDecimal.valueOf(1_000_000L), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private Double toMbps(Long bps) {
        if (bps == null) {
            return null;
        }
        return BigDecimal.valueOf(bps)
                .divide(BigDecimal.valueOf(1_000_000L), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private Object utilization(DeviceMetrics metric) {
        if (metric.getInterfaceSpeed() == null || metric.getInterfaceSpeed() <= 0) {
            return EMPTY_DATA_FROM_DATABASE;
        }
        long totalBps = defaultLong(metric.getTrafficRxBps()) + defaultLong(metric.getTrafficTxBps());
        return BigDecimal.valueOf(totalBps * 100.0d / metric.getInterfaceSpeed())
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
    }

    private BigDecimal average(List<BigDecimal> values) {
        List<BigDecimal> nonNullValues = values.stream().filter(Objects::nonNull).toList();
        if (nonNullValues.isEmpty()) {
            return null;
        }
        BigDecimal total = nonNullValues.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(nonNullValues.size()), 2, RoundingMode.HALF_UP);
    }

    private String formatBandwidth(long bps) {
        if (bps >= 1_000_000_000L) {
            return BigDecimal.valueOf(bps).divide(BigDecimal.valueOf(1_000_000_000L), 2, RoundingMode.HALF_UP) + " Gbps";
        }
        if (bps >= 1_000_000L) {
            return BigDecimal.valueOf(bps).divide(BigDecimal.valueOf(1_000_000L), 2, RoundingMode.HALF_UP) + " Mbps";
        }
        if (bps >= 1_000L) {
            return BigDecimal.valueOf(bps).divide(BigDecimal.valueOf(1_000L), 2, RoundingMode.HALF_UP) + " Kbps";
        }
        return bps + " bps";
    }

    private BigDecimal percentage(long value, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value * 100.0d / total).setScale(2, RoundingMode.HALF_UP);
    }

    private String stringValue(String value) {
        return value != null && !value.isBlank() ? value : EMPTY_DATA_FROM_DATABASE;
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return normalize(value) != null;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private long defaultLong(Long value) {
        return value != null ? value : 0L;
    }

    private int defaultInteger(Integer value) {
        return value != null ? value : 0;
    }

    private boolean isEmptyData(Object data) {
        if (data == null) {
            return true;
        }
        if (data instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (data instanceof Map<?, ?> map) {
            return map.isEmpty() || map.values().stream().allMatch(this::isEmptyValue);
        }
        return false;
    }

    private boolean isEmptyValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String stringValue) {
            return EMPTY_DATA_FROM_DATABASE.equals(stringValue);
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue() == 0.0d;
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty() || map.values().stream().allMatch(this::isEmptyValue);
        }
        return false;
    }

    private <T> ResponseEntity<ApiResponse<T>> errorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(message));
    }

    private record RealtimeTarget(MikrotikConnectionService.ConnectionTarget target, String source) {
    }

    private record LiveTrafficResult(RealtimeTarget target,
                                     MikrotikRouterOsApiClient.MikrotikLiveTrafficSnapshot snapshot) {
    }

    private record ServerMonitoringContext(
            Server server,
            MikrotikDevice mikrotik,
            NetworkDevice networkDevice,
            HttpStatus errorStatus,
            String errorMessage) {

        private static ServerMonitoringContext success(Server server, MikrotikDevice mikrotik, NetworkDevice networkDevice) {
            return new ServerMonitoringContext(server, mikrotik, networkDevice, null, null);
        }

        private static ServerMonitoringContext error(HttpStatus status, String message) {
            return new ServerMonitoringContext(null, null, null, status, message);
        }

        private boolean hasError() {
            return errorStatus != null;
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> databaseResponse(String successMessage, T data) {
        return ResponseEntity.ok(ApiResponse.success(isEmptyData(data) ? EMPTY_DATA_FROM_DATABASE : successMessage, data));
    }
}
