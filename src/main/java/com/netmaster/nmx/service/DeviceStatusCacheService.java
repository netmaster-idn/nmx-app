package com.netmaster.nmx.service;

import com.netmaster.nmx.model.DeviceHealthSnapshot;
import com.netmaster.nmx.model.DeviceMaintenanceWindow;
import com.netmaster.nmx.model.DeviceMetrics;
import com.netmaster.nmx.model.DeviceStatusCache;
import com.netmaster.nmx.model.NetworkAlert;
import com.netmaster.nmx.model.NetworkDevice;
import com.netmaster.nmx.model.NetworkDeviceSyncStatus;
import com.netmaster.nmx.repository.DeviceHealthSnapshotRepository;
import com.netmaster.nmx.repository.DeviceMaintenanceWindowRepository;
import com.netmaster.nmx.repository.DeviceMetricsRepository;
import com.netmaster.nmx.repository.DeviceStatusCacheRepository;
import com.netmaster.nmx.repository.NetworkAlertRepository;
import com.netmaster.nmx.repository.NetworkDeviceRepository;
import com.netmaster.nmx.repository.NetworkDeviceSyncStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceStatusCacheService {

    private static final int SNAPSHOT_INTERVAL_SECONDS = 120;
    private static final int SNAPSHOT_RETENTION_DAYS = 14;

    private final NetworkDeviceRepository networkDeviceRepository;
    private final DeviceMetricsRepository deviceMetricsRepository;
    private final NetworkAlertRepository networkAlertRepository;
    private final DeviceStatusCacheRepository deviceStatusCacheRepository;
    private final DeviceHealthSnapshotRepository deviceHealthSnapshotRepository;
    private final DeviceMaintenanceWindowRepository deviceMaintenanceWindowRepository;
    private final NetworkDeviceSyncStatusRepository networkDeviceSyncStatusRepository;
    private final DeviceStatusFreshnessService freshnessService;
    private final DeviceStatusScoringService scoringService;
    private final NetworkDeviceSyncStatusService syncStatusService;

    @Scheduled(fixedDelay = 60000, initialDelay = 25000)
    @Transactional
    public void refreshStatusCache() {
        List<NetworkDevice> devices = networkDeviceRepository.findByIsActiveTrue();
        if (devices.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Long, DeviceMetrics> metricsByDeviceId = latestMetricsByDeviceId(devices);
        Map<Long, List<NetworkAlert>> activeAlertsByDeviceId = networkAlertRepository.findActiveAlerts().stream()
                .filter(alert -> alert.getDeviceId() != null)
                .collect(Collectors.groupingBy(NetworkAlert::getDeviceId));
        Map<Long, DeviceMaintenanceWindow> maintenanceByDeviceId = deviceMaintenanceWindowRepository.findActiveWindows(now).stream()
                .collect(Collectors.toMap(window -> window.getDevice().getId(), window -> window, (left, right) -> left));
        Map<Long, List<NetworkDeviceSyncStatus>> syncStatusesByDeviceId = networkDeviceSyncStatusRepository.findAll().stream()
                .filter(status -> status.getDevice() != null && status.getDevice().getId() != null)
                .collect(Collectors.groupingBy(status -> status.getDevice().getId()));

        for (NetworkDevice device : devices) {
            if (!syncStatusService.isExecutionAllowed(device, NetworkDeviceSyncStatusService.MODULE_DEVICE_HEALTH_SNAPSHOT)) {
                continue;
            }
            try {
                syncStatusService.recordAttempt(device, NetworkDeviceSyncStatusService.MODULE_DEVICE_HEALTH_SNAPSHOT, 300);
                refreshDeviceCache(
                        device,
                        metricsByDeviceId.get(device.getId()),
                        activeAlertsByDeviceId.getOrDefault(device.getId(), List.of()),
                        maintenanceByDeviceId.get(device.getId()),
                        syncStatusesByDeviceId.getOrDefault(device.getId(), List.of()),
                        now
                );
                syncStatusService.recordSuccess(device, NetworkDeviceSyncStatusService.MODULE_DEVICE_HEALTH_SNAPSHOT, 0L, 1, 300);
            } catch (Exception ex) {
                syncStatusService.recordFailure(device, NetworkDeviceSyncStatusService.MODULE_DEVICE_HEALTH_SNAPSHOT, ex.getMessage(), 300);
                log.warn("Failed to refresh health cache for {}: {}", device.getDeviceName(), ex.getMessage());
            }
        }

        trimOldSnapshots();
        log.debug("Refreshed device status cache for {} devices", devices.size());
    }

    @Transactional
    public Optional<DeviceStatusCache> refreshDeviceCache(Long deviceId) {
        return networkDeviceRepository.findById(deviceId).map(device -> {
            LocalDateTime now = LocalDateTime.now();
            DeviceMetrics metrics = deviceMetricsRepository.findTopByDeviceIdOrderByTimestampDesc(deviceId).orElse(null);
            List<NetworkAlert> alerts = networkAlertRepository.findRelatedAlertsByDevice(deviceId);
            DeviceMaintenanceWindow maintenanceWindow = deviceMaintenanceWindowRepository
                    .findActiveWindowByDeviceId(deviceId, now)
                    .orElse(null);
            List<NetworkDeviceSyncStatus> syncStatuses = networkDeviceSyncStatusRepository.findByDeviceIdOrderByModuleNameAsc(deviceId);
            return refreshDeviceCache(device, metrics, alerts, maintenanceWindow, syncStatuses, now);
        });
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary() {
        return buildSummary(deviceStatusCacheRepository.findAll());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(String search,
                                          String deviceType,
                                          String location,
                                          String status,
                                          String freshness) {
        return buildSummary(filterCaches(deviceStatusCacheRepository.findAll(), search, deviceType, location, status, freshness));
    }

    @Transactional(readOnly = true)
    public List<Long> getMatchingMikrotikSourceIds(String search,
                                                   String deviceType,
                                                   String location,
                                                   String status,
                                                   String freshness) {
        return filterCaches(deviceStatusCacheRepository.findAll(), search, deviceType, location, status, freshness).stream()
                .filter(cache -> cache.getDevice() != null)
                .filter(cache -> cache.getDevice().getSourceId() != null)
                .filter(cache -> "mikrotik".equalsIgnoreCase(cache.getDevice().getSourceType()))
                .map(cache -> cache.getDevice().getSourceId())
                .distinct()
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDevicePage(String search,
                                             String deviceType,
                                             String location,
                                             String status,
                                             String freshness,
                                             String sortBy,
                                             String direction,
                                             int page,
                                             int size) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, 100)),
                buildSort(sortBy, direction)
        );

        Page<DeviceStatusCache> result = deviceStatusCacheRepository.search(
                normalizeBlank(deviceType),
                normalizeBlank(location),
                normalizeBlank(status),
                normalizeBlank(freshness),
                normalizeBlank(search),
                pageable
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", result.getContent().stream().map(this::toDeviceListItem).toList());
        payload.put("page", result.getNumber());
        payload.put("size", result.getSize());
        payload.put("totalItems", result.getTotalElements());
        payload.put("totalPages", result.getTotalPages());
        payload.put("lastSyncAt", result.getContent().stream()
                .map(DeviceStatusCache::getLastSyncAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null));
        return payload;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDeviceHealth(Long deviceId) {
        DeviceStatusCache cache = deviceStatusCacheRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device status not found"));

        List<DeviceHealthSnapshot> snapshots = deviceHealthSnapshotRepository
                .findTop48ByDeviceIdOrderByCapturedAtDesc(deviceId)
                .stream()
                .sorted(Comparator.comparing(DeviceHealthSnapshot::getCapturedAt))
                .toList();
        List<NetworkAlert> alerts = networkAlertRepository.findRelatedAlertsByDevice(deviceId);
        List<NetworkDeviceSyncStatus> syncStatuses = networkDeviceSyncStatusRepository.findByDeviceIdOrderByModuleNameAsc(deviceId);
        List<DeviceMaintenanceWindow> maintenanceWindows = deviceMaintenanceWindowRepository
                .findByEndsAtAfterOrderByStartsAtAsc(LocalDateTime.now().minusDays(1))
                .stream()
                .filter(window -> window.getDevice() != null && Objects.equals(window.getDevice().getId(), deviceId))
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device", toDeviceListItem(cache));
        payload.put("healthClass", scoringService.classifyHealth(defaultInteger(cache.getHealthScore())));
        payload.put("snapshots", snapshots.stream().map(snapshot -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("capturedAt", snapshot.getCapturedAt());
            item.put("status", snapshot.getStatus());
            item.put("cpuUsage", snapshot.getCpuUsage());
            item.put("memoryUsage", snapshot.getMemoryUsage());
            item.put("latencyMs", snapshot.getLatencyMs());
            item.put("packetLoss", snapshot.getPacketLoss());
            item.put("healthScore", snapshot.getHealthScore());
            item.put("freshnessStatus", snapshot.getFreshnessStatus());
            return item;
        }).toList());
        payload.put("alerts", alerts.stream().limit(10).map(this::toAlertItem).toList());
        payload.put("syncStatus", syncStatuses.stream().map(this::toSyncStatusItem).toList());
        payload.put("maintenance", maintenanceWindows.stream().limit(5).map(this::toMaintenanceItem).toList());
        return payload;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAlertSummary() {
        List<NetworkAlert> alerts = networkAlertRepository.findActiveAlerts();
        Map<String, Long> severity = alerts.stream()
                .collect(Collectors.groupingBy(alert -> safeLower(alert.getSeverity()), Collectors.counting()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalActive", alerts.size());
        payload.put("critical", severity.getOrDefault("critical", 0L));
        payload.put("major", severity.getOrDefault("major", 0L));
        payload.put("warning", severity.getOrDefault("warning", 0L));
        payload.put("info", severity.getOrDefault("info", 0L));
        payload.put("recent", alerts.stream().limit(8).map(this::toAlertItem).toList());
        return payload;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAlerts(String severity, String status, int limit) {
        return networkAlertRepository.findByFilters(
                        normalizeBlank(severity),
                        normalizeBlank(status),
                        null,
                        null,
                        null,
                        null,
                        null
                ).stream()
                .limit(Math.max(1, Math.min(limit, 100)))
                .map(this::toAlertItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProblematicDevices() {
        List<DeviceStatusCache> caches = deviceStatusCacheRepository.findAll();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("topAlerting", caches.stream()
                .sorted(Comparator.comparing(DeviceStatusCache::getAlertCount, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(DeviceStatusCache::getHealthScore, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(5)
                .map(this::toProblemItem)
                .toList());
        payload.put("topStale", caches.stream()
                .filter(cache -> isOneOf(cache.getFreshnessStatus(), "stale", "unreachable"))
                .sorted(Comparator.comparing(DeviceStatusCache::getLastSyncAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(5)
                .map(this::toProblemItem)
                .toList());
        payload.put("topCpu", caches.stream()
                .filter(cache -> cache.getCpuUsage() != null)
                .sorted(Comparator.comparing(DeviceStatusCache::getCpuUsage).reversed())
                .limit(5)
                .map(this::toProblemItem)
                .toList());
        payload.put("topOffline", caches.stream()
                .filter(cache -> isOneOf(cache.getStatus(), "offline"))
                .sorted(Comparator.comparing(DeviceStatusCache::getLastSeen, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(5)
                .map(this::toProblemItem)
                .toList());
        return payload;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMaintenanceList() {
        return deviceMaintenanceWindowRepository.findByEndsAtAfterOrderByStartsAtAsc(LocalDateTime.now().minusDays(1)).stream()
                .limit(20)
                .map(this::toMaintenanceItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getHealthTrends(int hours) {
        int boundedHours = Math.max(1, Math.min(hours, 168));
        LocalDateTime since = LocalDateTime.now().minusHours(boundedHours);
        List<DeviceHealthSnapshot> snapshots = deviceHealthSnapshotRepository.findByCapturedAtAfterOrderByCapturedAtAsc(since);

        Map<String, List<DeviceHealthSnapshot>> grouped = snapshots.stream()
                .collect(Collectors.groupingBy(snapshot -> snapshot.getCapturedAt().withMinute(0).withSecond(0).withNano(0).toString()));

        List<Map<String, Object>> series = grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<DeviceHealthSnapshot> bucket = entry.getValue();
                    long online = bucket.stream().filter(snapshot -> isOneOf(snapshot.getStatus(), "online")).count();
                    long offline = bucket.stream().filter(snapshot -> isOneOf(snapshot.getStatus(), "offline")).count();
                    long warning = bucket.stream().filter(snapshot -> isOneOf(snapshot.getStatus(), "warning")).count();
                    long stale = bucket.stream().filter(snapshot -> isOneOf(snapshot.getFreshnessStatus(), "stale", "unreachable")).count();

                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("time", entry.getKey());
                    item.put("online", online);
                    item.put("offline", offline);
                    item.put("warning", warning);
                    item.put("stale", stale);
                    item.put("avgHealthScore", averageInteger(bucket.stream().map(DeviceHealthSnapshot::getHealthScore).toList()));
                    return item;
                })
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("hours", boundedHours);
        payload.put("series", series);
        return payload;
    }

    private DeviceStatusCache refreshDeviceCache(NetworkDevice device,
                                                 DeviceMetrics metrics,
                                                 List<NetworkAlert> activeAlerts,
                                                 DeviceMaintenanceWindow maintenanceWindow,
                                                 List<NetworkDeviceSyncStatus> syncStatuses,
                                                 LocalDateTime now) {
        LocalDateTime lastSeen = resolveLastSeen(device, metrics, syncStatuses);
        String baseStatus = resolveStatus(device, maintenanceWindow, metrics);
        String freshness = freshnessService.evaluate(lastSeen, baseStatus);
        int alertCount = (int) activeAlerts.stream().filter(alert -> !"closed".equalsIgnoreCase(alert.getStatus())).count();
        boolean hasMaintenance = maintenanceWindow != null;
        int healthScore = scoringService.calculateHealthScore(
                baseStatus,
                freshness,
                hasMaintenance,
                alertCount,
                metrics != null ? metrics.getCpuUsage() : null,
                metrics != null ? metrics.getMemoryUsage() : null,
                metrics != null ? metrics.getLatencyMs() : null,
                metrics != null ? metrics.getPacketLoss() : null
        );

        DeviceStatusCache cache = deviceStatusCacheRepository.findByDeviceId(device.getId())
                .orElseGet(DeviceStatusCache::new);
        cache.setDevice(device);
        cache.setDeviceName(device.getDeviceName());
        cache.setIpAddress(device.getIpAddress());
        cache.setRole(device.getDeviceType() != null ? device.getDeviceType().name() : null);
        cache.setLocation(device.getLocation());
        cache.setStatus(baseStatus);
        cache.setMaintenanceStatus(hasMaintenance ? "active" : "inactive");
        cache.setWarningStatus("warning".equals(baseStatus) || alertCount > 0 ? "active" : "normal");
        cache.setCpuUsage(metrics != null ? metrics.getCpuUsage() : null);
        cache.setMemoryUsage(metrics != null ? metrics.getMemoryUsage() : null);
        cache.setUptime(metrics != null && metrics.getUptimeSeconds() != null ? metrics.getUptimeSeconds() : device.getUptimeSeconds());
        cache.setLatencyMs(metrics != null ? metrics.getLatencyMs() : null);
        cache.setPacketLoss(metrics != null ? metrics.getPacketLoss() : null);
        cache.setHealthScore(healthScore);
        cache.setFreshnessStatus(freshness);
        cache.setAlertCount(alertCount);
        cache.setLastSeen(lastSeen);
        cache.setLastPingSuccessAt(device.getLastPingTime());
        cache.setLastApiSuccessAt(resolveLastApiSuccess(syncStatuses, metrics));
        cache.setLastSyncAt(resolveLastSyncAt(syncStatuses, metrics, device));
        cache.setSyncSource(resolveSyncSource(syncStatuses, device, metrics));
        cache.setMaintenanceReason(hasMaintenance ? maintenanceWindow.getReason() : null);
        cache.setMaintenanceStartsAt(hasMaintenance ? maintenanceWindow.getStartsAt() : null);
        cache.setMaintenanceEndsAt(hasMaintenance ? maintenanceWindow.getEndsAt() : null);
        cache.setUpdatedAt(now);

        DeviceStatusCache saved = deviceStatusCacheRepository.save(cache);
        maybeCaptureSnapshot(saved, now);
        return saved;
    }

    private void maybeCaptureSnapshot(DeviceStatusCache cache, LocalDateTime now) {
        List<DeviceHealthSnapshot> latest = deviceHealthSnapshotRepository.findTop48ByDeviceIdOrderByCapturedAtDesc(cache.getDevice().getId());
        if (!latest.isEmpty() && latest.get(0).getCapturedAt() != null
                && latest.get(0).getCapturedAt().isAfter(now.minusSeconds(SNAPSHOT_INTERVAL_SECONDS))) {
            return;
        }

        deviceHealthSnapshotRepository.save(DeviceHealthSnapshot.builder()
                .device(cache.getDevice())
                .status(cache.getStatus())
                .cpuUsage(cache.getCpuUsage())
                .memoryUsage(cache.getMemoryUsage())
                .latencyMs(cache.getLatencyMs())
                .packetLoss(cache.getPacketLoss())
                .healthScore(cache.getHealthScore())
                .freshnessStatus(cache.getFreshnessStatus())
                .capturedAt(now)
                .build());
    }

    private void trimOldSnapshots() {
        deviceHealthSnapshotRepository.deleteByCapturedAtBefore(LocalDateTime.now().minusDays(SNAPSHOT_RETENTION_DAYS));
    }

    private Map<Long, DeviceMetrics> latestMetricsByDeviceId(List<NetworkDevice> devices) {
        List<Long> deviceIds = devices.stream().map(NetworkDevice::getId).toList();
        if (deviceIds.isEmpty()) {
            return Map.of();
        }
        return deviceMetricsRepository.findLatestSummaryByDeviceIds(deviceIds).stream()
                .collect(Collectors.toMap(metric -> metric.getDevice().getId(), metric -> metric, (left, right) -> left));
    }

    private Map<String, Object> toDeviceListItem(DeviceStatusCache cache) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("deviceId", cache.getDevice().getId());
        item.put("deviceName", cache.getDeviceName());
        item.put("ipAddress", cache.getIpAddress());
        item.put("role", cache.getRole());
        item.put("location", cache.getLocation());
        item.put("status", cache.getStatus());
        item.put("maintenanceStatus", cache.getMaintenanceStatus());
        item.put("healthScore", cache.getHealthScore());
        item.put("healthClass", scoringService.classifyHealth(defaultInteger(cache.getHealthScore())));
        item.put("cpuUsage", cache.getCpuUsage());
        item.put("memoryUsage", cache.getMemoryUsage());
        item.put("latencyMs", cache.getLatencyMs());
        item.put("packetLoss", cache.getPacketLoss());
        item.put("lastSeen", cache.getLastSeen());
        item.put("lastSyncAt", cache.getLastSyncAt());
        item.put("lastPingSuccessAt", cache.getLastPingSuccessAt());
        item.put("lastApiSuccessAt", cache.getLastApiSuccessAt());
        item.put("freshnessStatus", cache.getFreshnessStatus());
        item.put("alertCount", cache.getAlertCount());
        item.put("syncSource", cache.getSyncSource());
        item.put("maintenanceReason", cache.getMaintenanceReason());
        item.put("maintenanceStartsAt", cache.getMaintenanceStartsAt());
        item.put("maintenanceEndsAt", cache.getMaintenanceEndsAt());
        item.put("uptimeSeconds", cache.getUptime());
        return item;
    }

    private Map<String, Object> toAlertItem(NetworkAlert alert) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", alert.getId());
        item.put("alertId", alert.getAlertId());
        item.put("deviceId", alert.getDeviceId());
        item.put("deviceName", alert.getDeviceName());
        item.put("deviceType", alert.getDeviceType());
        item.put("location", alert.getLocation());
        item.put("severity", alert.getSeverity());
        item.put("status", alert.getStatus());
        item.put("alertType", alert.getAlertType());
        item.put("message", alert.getMessage());
        item.put("source", alert.getSource());
        item.put("createdAt", alert.getCreatedAt());
        return item;
    }

    private Map<String, Object> toSyncStatusItem(NetworkDeviceSyncStatus status) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("moduleName", status.getModuleName());
        item.put("status", status.getStatus());
        item.put("lastAttemptAt", status.getLastAttemptAt());
        item.put("lastSuccessAt", status.getLastSuccessAt());
        item.put("lastError", status.getLastError());
        item.put("failCount", status.getFailCount());
        item.put("staleAfterSeconds", status.getStaleAfterSeconds());
        item.put("breakerUntil", status.getBreakerUntil());
        item.put("lastDurationMs", status.getLastDurationMs());
        item.put("lastItemCount", status.getLastItemCount());
        return item;
    }

    private Map<String, Object> toMaintenanceItem(DeviceMaintenanceWindow window) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", window.getId());
        item.put("deviceId", window.getDevice() != null ? window.getDevice().getId() : null);
        item.put("deviceName", window.getDevice() != null ? window.getDevice().getDeviceName() : null);
        item.put("location", window.getDevice() != null ? window.getDevice().getLocation() : null);
        item.put("startsAt", window.getStartsAt());
        item.put("endsAt", window.getEndsAt());
        item.put("reason", window.getReason());
        item.put("createdBy", window.getCreatedBy());
        item.put("active", window.getIsActive());
        return item;
    }

    private Map<String, Object> toProblemItem(DeviceStatusCache cache) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("deviceId", cache.getDevice().getId());
        item.put("deviceName", cache.getDeviceName());
        item.put("status", cache.getStatus());
        item.put("freshnessStatus", cache.getFreshnessStatus());
        item.put("healthScore", cache.getHealthScore());
        item.put("alertCount", cache.getAlertCount());
        item.put("cpuUsage", cache.getCpuUsage());
        item.put("lastSyncAt", cache.getLastSyncAt());
        return item;
    }

    private Map<String, Object> buildGlobalSyncStatus() {
        List<NetworkDeviceSyncStatus> statuses = networkDeviceSyncStatusRepository.findAll();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("modules", statuses.stream()
                .collect(Collectors.groupingBy(NetworkDeviceSyncStatus::getModuleName, Collectors.counting())));
        payload.put("lastSuccessAt", statuses.stream()
                .map(NetworkDeviceSyncStatus::getLastSuccessAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null));
        payload.put("failingModules", statuses.stream()
                .filter(status -> isOneOf(status.getStatus(), "failed"))
                .map(NetworkDeviceSyncStatus::getModuleName)
                .distinct()
                .toList());
        return payload;
    }

    private Map<String, Object> buildSummary(List<DeviceStatusCache> caches) {
        long total = caches.size();
        long online = countStatus(caches, "online");
        long offline = countStatus(caches, "offline");
        long maintenance = countStatus(caches, "maintenance");
        long warning = countStatus(caches, "warning");
        long stale = caches.stream().filter(cache -> isOneOf(cache.getFreshnessStatus(), "stale", "unreachable")).count();
        long criticalAlerts = caches.stream()
                .filter(cache -> cache.getAlertCount() != null && cache.getAlertCount() > 0)
                .filter(cache -> cache.getHealthScore() != null && cache.getHealthScore() < 70)
                .count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalDevice", total);
        summary.put("onlineCount", online);
        summary.put("offlineCount", offline);
        summary.put("maintenanceCount", maintenance);
        summary.put("warningCount", warning);
        summary.put("staleCount", stale);
        summary.put("avgCpuSnapshot", average(caches.stream().map(DeviceStatusCache::getCpuUsage).toList()));
        summary.put("avgMemorySnapshot", average(caches.stream().map(DeviceStatusCache::getMemoryUsage).toList()));
        summary.put("criticalAlertCount", criticalAlerts);
        summary.put("lastSyncAt", caches.stream()
                .map(DeviceStatusCache::getLastSyncAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null));
        summary.put("syncSourceStatus", buildGlobalSyncStatus());
        summary.put("filters", Map.of(
                "roles", deviceStatusCacheRepository.findDistinctRoles(),
                "locations", deviceStatusCacheRepository.findDistinctLocations()
        ));
        return summary;
    }

    private List<DeviceStatusCache> filterCaches(List<DeviceStatusCache> caches,
                                                 String search,
                                                 String deviceType,
                                                 String location,
                                                 String status,
                                                 String freshness) {
        return caches.stream()
                .filter(cache -> matchesEquals(deviceType, cache.getRole()))
                .filter(cache -> matchesEquals(location, cache.getLocation()))
                .filter(cache -> matchesEquals(status, cache.getStatus()))
                .filter(cache -> matchesEquals(freshness, cache.getFreshnessStatus()))
                .filter(cache -> matchesSearch(search, cache.getDeviceName(), cache.getIpAddress(), cache.getRole(), cache.getLocation()))
                .toList();
    }

    private boolean matchesEquals(String expected, String actual) {
        if (normalizeBlank(expected) == null) {
            return true;
        }
        if (normalizeBlank(actual) == null) {
            return false;
        }
        return actual.trim().equalsIgnoreCase(expected.trim());
    }

    private boolean matchesSearch(String search, String... values) {
        if (normalizeBlank(search) == null) {
            return true;
        }
        String keyword = search.trim().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Sort buildSort(String sortBy, String direction) {
        String property = switch (safeLower(sortBy)) {
            case "status" -> "status";
            case "lastsync", "last_sync", "lastsyncat" -> "lastSyncAt";
            case "alertcount", "alerts" -> "alertCount";
            case "healthscore", "health" -> "healthScore";
            case "freshness" -> "freshnessStatus";
            default -> "deviceName";
        };

        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(sortDirection, property);
    }

    private String resolveStatus(NetworkDevice device, DeviceMaintenanceWindow maintenanceWindow, DeviceMetrics metrics) {
        if (maintenanceWindow != null) {
            return "maintenance";
        }
        if (device.getStatus() != null) {
            return device.getStatus().name().toLowerCase(Locale.ROOT);
        }
        if (metrics != null && metrics.isCritical()) {
            return "warning";
        }
        return "unknown";
    }

    private LocalDateTime resolveLastSeen(NetworkDevice device, DeviceMetrics metrics, List<NetworkDeviceSyncStatus> syncStatuses) {
        List<LocalDateTime> candidates = new ArrayList<>();
        if (device.getLastPingTime() != null) {
            candidates.add(device.getLastPingTime());
        }
        if (metrics != null && metrics.getTimestamp() != null) {
            candidates.add(metrics.getTimestamp());
        }
        syncStatuses.stream()
                .map(NetworkDeviceSyncStatus::getLastSuccessAt)
                .filter(Objects::nonNull)
                .forEach(candidates::add);
        return candidates.stream().max(Comparator.naturalOrder()).orElse(null);
    }

    private LocalDateTime resolveLastApiSuccess(List<NetworkDeviceSyncStatus> syncStatuses, DeviceMetrics metrics) {
        List<LocalDateTime> candidates = syncStatuses.stream()
                .filter(status -> !NetworkDeviceSyncStatusService.MODULE_PING_STATUS.equals(status.getModuleName()))
                .map(NetworkDeviceSyncStatus::getLastSuccessAt)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        if (metrics != null && metrics.getTimestamp() != null) {
            candidates.add(metrics.getTimestamp());
        }
        return candidates.stream().max(Comparator.naturalOrder()).orElse(null);
    }

    private LocalDateTime resolveLastSyncAt(List<NetworkDeviceSyncStatus> syncStatuses, DeviceMetrics metrics, NetworkDevice device) {
        List<LocalDateTime> candidates = syncStatuses.stream()
                .map(NetworkDeviceSyncStatus::getLastAttemptAt)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        if (metrics != null && metrics.getTimestamp() != null) {
            candidates.add(metrics.getTimestamp());
        }
        if (device.getUpdatedAt() != null) {
            candidates.add(device.getUpdatedAt());
        }
        return candidates.stream().max(Comparator.naturalOrder()).orElse(null);
    }

    private String resolveSyncSource(List<NetworkDeviceSyncStatus> syncStatuses, NetworkDevice device, DeviceMetrics metrics) {
        boolean pingOk = syncStatuses.stream()
                .anyMatch(status -> NetworkDeviceSyncStatusService.MODULE_PING_STATUS.equals(status.getModuleName())
                        && isOneOf(status.getStatus(), "success"));
        boolean apiOk = syncStatuses.stream()
                .anyMatch(status -> !NetworkDeviceSyncStatusService.MODULE_PING_STATUS.equals(status.getModuleName())
                        && isOneOf(status.getStatus(), "success"));
        if (pingOk && apiOk) {
            return "ping+api";
        }
        if (apiOk || metrics != null) {
            return "api_only";
        }
        if (pingOk || device.getLastPingTime() != null) {
            return "ping_only";
        }
        return "cached_stale";
    }

    private BigDecimal average(Collection<BigDecimal> values) {
        List<BigDecimal> filtered = values.stream().filter(Objects::nonNull).toList();
        if (filtered.isEmpty()) {
            return null;
        }
        BigDecimal total = filtered.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(filtered.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal averageInteger(Collection<Integer> values) {
        List<Integer> filtered = values.stream().filter(Objects::nonNull).toList();
        if (filtered.isEmpty()) {
            return null;
        }
        int total = filtered.stream().mapToInt(Integer::intValue).sum();
        return BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(filtered.size()), 2, RoundingMode.HALF_UP);
    }

    private long countStatus(List<DeviceStatusCache> caches, String status) {
        return caches.stream().filter(cache -> isOneOf(cache.getStatus(), status)).count();
    }

    private boolean isOneOf(String value, String... candidates) {
        String safeValue = safeLower(value);
        for (String candidate : candidates) {
            if (safeValue.equals(safeLower(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Integer defaultInteger(Integer value) {
        return value != null ? value : 0;
    }
}
