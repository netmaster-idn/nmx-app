package com.netmaster.nmx.service;

import com.netmaster.nmx.model.NetworkAlert;
import com.netmaster.nmx.repository.NetworkAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonitoringCommandCenterService {

    private final DeviceStatusCacheService deviceStatusCacheService;
    private final MikrotikMonitoringQueryService mikrotikMonitoringQueryService;
    private final NetworkAlertRepository networkAlertRepository;

    public Map<String, Object> getOverview(String search,
                                           String deviceType,
                                           String location,
                                           String status,
                                           String freshness) {
        Map<String, Object> deviceSummary = deviceStatusCacheService.getSummary(search, deviceType, location, status, freshness);
        List<Long> allowedMikrotikIds = deviceStatusCacheService.getMatchingMikrotikSourceIds(search, deviceType, location, status, freshness);
        Map<String, Object> trafficSummary = mikrotikMonitoringQueryService.getInterfaceSummary(allowedMikrotikIds);
        Map<String, Object> pppActiveSummary = mikrotikMonitoringQueryService.getPppoeActiveSummary(allowedMikrotikIds);
        Map<String, Object> pppEventSummary = mikrotikMonitoringQueryService.getPppoeEventSummary(allowedMikrotikIds);
        Map<String, Object> alertSummary = getAlertsSummary(search, deviceType, location);
        Map<String, Object> problematic = deviceStatusCacheService.getProblematicDevices();
        Map<String, Object> topInterfaces = mikrotikMonitoringQueryService.getTopInterfaces(5, null, allowedMikrotikIds);
        Map<String, Object> topTrafficDevices = mikrotikMonitoringQueryService.getTopDevicesByTraffic(5, allowedMikrotikIds);

        Map<String, Object> cards = new LinkedHashMap<>();
        Map<String, Object> trafficTotals = mapValue(trafficSummary.get("totals"));
        cards.put("totalDevices", deviceSummary.get("totalDevice"));
        cards.put("online", deviceSummary.get("onlineCount"));
        cards.put("offline", deviceSummary.get("offlineCount"));
        cards.put("maintenance", deviceSummary.get("maintenanceCount"));
        cards.put("warning", deviceSummary.get("warningCount"));
        cards.put("staleData", deviceSummary.get("staleCount"));
        cards.put("criticalAlerts", deviceSummary.get("criticalAlertCount"));
        cards.put("totalActivePppoe", pppActiveSummary.get("totalActive"));
        cards.put("totalTrafficInBps", trafficTotals.getOrDefault("rxBps", 0L));
        cards.put("totalTrafficOutBps", trafficTotals.getOrDefault("txBps", 0L));
        cards.put("totalTrafficInMbps", trafficTotals.getOrDefault("rxMbps", 0d));
        cards.put("totalTrafficOutMbps", trafficTotals.getOrDefault("txMbps", 0d));
        cards.put("avgCpuSnapshot", deviceSummary.get("avgCpuSnapshot"));
        cards.put("avgMemorySnapshot", deviceSummary.get("avgMemorySnapshot"));

        Map<String, Object> freshnessByDomain = new LinkedHashMap<>();
        freshnessByDomain.put("devices", buildSectionFreshness(
                "devices",
                valueAsDateTime(deviceSummary.get("lastSyncAt")),
                summarizeSyncSource(mapValue(deviceSummary.get("syncSourceStatus")))
        ));
        freshnessByDomain.put("traffic", trafficSummary.get("freshness"));
        freshnessByDomain.put("pppActive", pppActiveSummary.get("freshness"));
        freshnessByDomain.put("pppEvents", pppEventSummary.get("freshness"));
        freshnessByDomain.put("alerts", buildSectionFreshness(
                "alerts",
                valueAsDateTime(deviceSummary.get("lastSyncAt")),
                alertSummary.isEmpty() ? "unreachable" : "fresh"
        ));

        LocalDateTime globalLastSync = latestSync(
                valueAsDateTime(deviceSummary.get("lastSyncAt")),
                valueAsDateTime(mapValue(trafficSummary.get("freshness")).get("lastSyncAt")),
                valueAsDateTime(mapValue(pppActiveSummary.get("freshness")).get("lastSyncAt")),
                valueAsDateTime(mapValue(pppEventSummary.get("freshness")).get("lastSyncAt"))
        );

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("cards", cards);
        overview.put("filters", deviceSummary.get("filters"));
        overview.put("globalLastSync", globalLastSync);
        overview.put("globalFreshness", summarizeDomains(freshnessByDomain));
        overview.put("sourceStatus", summarizeDomains(freshnessByDomain));
        overview.put("freshness", freshnessByDomain);
        overview.put("problematic", problematic);
        overview.put("topInterfaces", topInterfaces.get("items"));
        overview.put("topTrafficDevices", topTrafficDevices.get("items"));
        overview.put("latestPppEvents", pppEventSummary.get("latestEvents"));
        overview.put("latestActiveUsers", pppActiveSummary.get("latestActiveUsers"));
        overview.put("recentAlerts", alertSummary.get("recent"));
        overview.put("syncStatus", deviceSummary.get("syncSourceStatus"));
        overview.put("alertSummary", alertSummary);
        return overview;
    }

    public Map<String, Object> getGlobalFilters() {
        Map<String, Object> summary = deviceStatusCacheService.getSummary();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceRoles", mapValue(summary.get("filters")).getOrDefault("roles", List.of()));
        payload.put("locations", mapValue(summary.get("filters")).getOrDefault("locations", List.of()));
        payload.put("deviceStatuses", List.of("online", "offline", "warning", "maintenance"));
        payload.put("freshnessStatuses", List.of("fresh", "delayed", "stale", "unreachable"));
        payload.put("timeWindows", List.of(
                Map.of("value", 60, "label", "60 menit"),
                Map.of("value", 180, "label", "3 jam"),
                Map.of("value", 360, "label", "6 jam")
        ));
        return payload;
    }

    public Map<String, Object> getTrafficSection(int minutes,
                                                 int limit,
                                                 String search,
                                                 String deviceType,
                                                 String location,
                                                 String status,
                                                 String freshness) {
        List<Long> allowedMikrotikIds = deviceStatusCacheService.getMatchingMikrotikSourceIds(search, deviceType, location, status, freshness);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", mikrotikMonitoringQueryService.getInterfaceSummary(allowedMikrotikIds));
        payload.put("timeline", mikrotikMonitoringQueryService.getTrafficTimeline(minutes, allowedMikrotikIds));
        payload.put("topInterfaces", mikrotikMonitoringQueryService.getTopInterfaces(limit, null, allowedMikrotikIds).get("items"));
        payload.put("topDevices", mikrotikMonitoringQueryService.getTopDevicesByTraffic(limit, allowedMikrotikIds).get("items"));
        return payload;
    }

    public Map<String, Object> getAlertsSummary(String search, String deviceType, String location) {
        List<NetworkAlert> alerts = networkAlertRepository.findByFilters(
                null,
                null,
                blankToNull(deviceType),
                blankToNull(location),
                blankToNull(search),
                null,
                null
        );
        Map<String, Object> base = summarizeAlerts(alerts);
        base.put("freshness", buildSectionFreshness(
                "alerts",
                latestAlertTimestamp(),
                alerts.isEmpty() ? "unreachable" : "fresh"
        ));
        base.put("topProblemDevices", networkAlertRepository.findTopProblemDevices(
                        LocalDateTime.now().minusHours(24),
                        org.springframework.data.domain.PageRequest.of(0, 5))
                .stream()
                .map(row -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("deviceName", row[0]);
                    item.put("count", row[1]);
                    return item;
                })
                .toList());
        return base;
    }

    public Map<String, Object> getAlertDetail(Long alertId) {
        NetworkAlert alert = networkAlertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert tidak ditemukan"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", alert.getId());
        payload.put("alertId", alert.getAlertId());
        payload.put("sourceType", safeText(alert.getSource(), "system"));
        payload.put("sourceId", alert.getDeviceId());
        payload.put("deviceId", alert.getDeviceId());
        payload.put("deviceName", alert.getDeviceName());
        payload.put("deviceType", alert.getDeviceType());
        payload.put("deviceIp", alert.getDeviceIp());
        payload.put("location", alert.getLocation());
        payload.put("alertType", alert.getAlertType());
        payload.put("severity", alert.getSeverity());
        payload.put("status", alert.getStatus());
        payload.put("title", safeText(alert.getAlertType(), "Alert"));
        payload.put("message", alert.getMessage());
        payload.put("metricType", alert.getMetricType());
        payload.put("metricValue", alert.getMetricValue());
        payload.put("threshold", alert.getThreshold());
        payload.put("affectedCustomers", alert.getAffectedCustomers());
        payload.put("slaImpact", alert.getSlaImpact());
        payload.put("acknowledgedAt", alert.getAcknowledgedAt());
        payload.put("resolvedAt", alert.getResolvedAt());
        payload.put("triggeredAt", alert.getCreatedAt());
        payload.put("updatedAt", alert.getUpdatedAt());
        return payload;
    }

    public Map<String, Object> getHealthTrends(int hours) {
        return deviceStatusCacheService.getHealthTrends(hours);
    }

    private Map<String, Object> summarizeAlerts(List<NetworkAlert> alerts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalActive", alerts.size());
        payload.put("critical", alerts.stream().filter(alert -> "critical".equalsIgnoreCase(alert.getSeverity())).count());
        payload.put("major", alerts.stream().filter(alert -> "major".equalsIgnoreCase(alert.getSeverity())).count());
        payload.put("warning", alerts.stream().filter(alert -> "warning".equalsIgnoreCase(alert.getSeverity())).count());
        payload.put("info", alerts.stream().filter(alert -> "info".equalsIgnoreCase(alert.getSeverity())).count());
        payload.put("recent", alerts.stream()
                .sorted(Comparator.comparing(NetworkAlert::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .map(alert -> {
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
                })
                .toList());
        return payload;
    }

    private LocalDateTime latestAlertTimestamp() {
        return networkAlertRepository.findRecentAlerts(1).stream()
                .map(NetworkAlert::getUpdatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private Map<String, Object> buildSectionFreshness(String module, LocalDateTime lastSyncAt, String fallbackStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("module", module);
        payload.put("lastSyncAt", lastSyncAt);
        payload.put("status", evaluateAgeStatus(lastSyncAt, fallbackStatus));
        payload.put("sourceStatus", fallbackStatus);
        return payload;
    }

    private String summarizeSyncSource(Map<String, Object> syncSource) {
        List<String> statuses = new ArrayList<>();
        Object failingModules = syncSource.get("failingModules");
        if (failingModules instanceof List<?> modules && !modules.isEmpty()) {
            return "degraded";
        }
        Object moduleMap = syncSource.get("modules");
        if (moduleMap instanceof Map<?, ?> modules) {
            modules.values().forEach(value -> statuses.add(String.valueOf(value)));
        }
        return statuses.isEmpty() ? "idle" : "healthy";
    }

    private String summarizeDomains(Map<String, Object> freshness) {
        List<String> statuses = freshness.values().stream()
                .map(this::mapValue)
                .map(item -> safeText(item.get("status"), "unknown").toLowerCase(Locale.ROOT))
                .toList();
        if (statuses.stream().anyMatch("unreachable"::equals)) {
            return "unreachable";
        }
        if (statuses.stream().anyMatch("stale"::equals)) {
            return "stale";
        }
        if (statuses.stream().anyMatch("delayed"::equals)) {
            return "delayed";
        }
        return "fresh";
    }

    private String evaluateAgeStatus(LocalDateTime lastSyncAt, String fallbackStatus) {
        if (lastSyncAt == null) {
            return "unreachable";
        }
        long ageSeconds = Duration.between(lastSyncAt, LocalDateTime.now()).getSeconds();
        if (ageSeconds > 900) {
            return "stale";
        }
        if (ageSeconds > 300) {
            return "delayed";
        }
        if ("degraded".equalsIgnoreCase(fallbackStatus)) {
            return "delayed";
        }
        return "fresh";
    }

    private LocalDateTime latestSync(LocalDateTime... timestamps) {
        LocalDateTime latest = null;
        for (LocalDateTime timestamp : timestamps) {
            if (timestamp == null) {
                continue;
            }
            if (latest == null || timestamp.isAfter(latest)) {
                latest = timestamp;
            }
        }
        return latest;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private LocalDateTime valueAsDateTime(Object value) {
        return value instanceof LocalDateTime timestamp ? timestamp : null;
    }

    private String safeText(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
