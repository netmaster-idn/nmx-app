package com.netmaster.nmx.service;

import com.netmaster.nmx.model.DeviceSyncStatus;
import com.netmaster.nmx.model.MikrotikDevice;
import com.netmaster.nmx.model.MikrotikInterface;
import com.netmaster.nmx.model.MikrotikInterfaceTraffic;
import com.netmaster.nmx.model.MikrotikPppoeEvent;
import com.netmaster.nmx.model.MikrotikPppoeSession;
import com.netmaster.nmx.repository.DeviceSyncStatusRepository;
import com.netmaster.nmx.repository.MikrotikDeviceRepository;
import com.netmaster.nmx.repository.MikrotikInterfaceRepository;
import com.netmaster.nmx.repository.MikrotikInterfaceTrafficRepository;
import com.netmaster.nmx.repository.MikrotikPppoeEventRepository;
import com.netmaster.nmx.repository.MikrotikPppoeSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MikrotikMonitoringQueryService {

    private final MikrotikInterfaceRepository mikrotikInterfaceRepository;
    private final MikrotikInterfaceTrafficRepository mikrotikInterfaceTrafficRepository;
    private final MikrotikPppoeSessionRepository mikrotikPppoeSessionRepository;
    private final MikrotikPppoeEventRepository mikrotikPppoeEventRepository;
    private final MikrotikDeviceRepository mikrotikDeviceRepository;
    private final DeviceSyncStatusRepository deviceSyncStatusRepository;

    public Map<String, Object> getInterfaceSummary() {
        return getInterfaceSummary(null);
    }

    public Map<String, Object> getInterfaceSummary(Collection<Long> allowedDeviceIds) {
        List<Map<String, Object>> latest = latestInterfaceRows(null, allowedDeviceIds);
        long totalRx = latest.stream().mapToLong(row -> valueAsLong(row.get("rxBps"))).sum();
        long totalTx = latest.stream().mapToLong(row -> valueAsLong(row.get("txBps"))).sum();
        long active = latest.stream().filter(row -> "up".equalsIgnoreCase(String.valueOf(row.get("status")))).count();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totals", Map.of(
                "rxBps", totalRx,
                "txBps", totalTx,
                "rxMbps", toMbps(totalRx),
                "txMbps", toMbps(totalTx),
                "activeInterfaces", active,
                "monitoredInterfaces", latest.size()
        ));
        payload.put("freshness", buildFreshness(MikrotikMonitoringManager.MODULE_INTERFACE_TRAFFIC));
        return payload;
    }

    public Map<String, Object> getTopInterfaces(Integer limit, Long deviceId) {
        return getTopInterfaces(limit, deviceId, null);
    }

    public Map<String, Object> getTopInterfaces(Integer limit, Long deviceId, Collection<Long> allowedDeviceIds) {
        int safeLimit = Math.max(limit != null ? limit : 5, 1);
        List<Map<String, Object>> latest = latestInterfaceRows(deviceId, allowedDeviceIds).stream()
                .limit(safeLimit)
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", latest);
        payload.put("freshness", buildFreshness(MikrotikMonitoringManager.MODULE_INTERFACE_TRAFFIC));
        return payload;
    }

    public Map<String, Object> getTopDevicesByTraffic(Integer limit) {
        return getTopDevicesByTraffic(limit, null);
    }

    public Map<String, Object> getTopDevicesByTraffic(Integer limit, Collection<Long> allowedDeviceIds) {
        int safeLimit = Math.max(limit != null ? limit : 5, 1);
        Map<Long, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : latestInterfaceRows(null, allowedDeviceIds)) {
            Long deviceId = valueAsLong(row.get("deviceId"));
            Map<String, Object> aggregate = grouped.computeIfAbsent(deviceId, ignored -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("deviceId", deviceId);
                item.put("deviceName", row.get("deviceName"));
                item.put("rxBps", 0L);
                item.put("txBps", 0L);
                item.put("interfaces", 0);
                item.put("capturedAt", row.get("capturedAt"));
                return item;
            });
            aggregate.put("rxBps", valueAsLong(aggregate.get("rxBps")) + valueAsLong(row.get("rxBps")));
            aggregate.put("txBps", valueAsLong(aggregate.get("txBps")) + valueAsLong(row.get("txBps")));
            aggregate.put("interfaces", ((Number) aggregate.get("interfaces")).intValue() + 1);
        }

        List<Map<String, Object>> items = grouped.values().stream()
                .peek(item -> {
                    long rxBps = valueAsLong(item.get("rxBps"));
                    long txBps = valueAsLong(item.get("txBps"));
                    item.put("rxMbps", toMbps(rxBps));
                    item.put("txMbps", toMbps(txBps));
                    item.put("totalBps", rxBps + txBps);
                    item.put("totalMbps", toMbps(rxBps + txBps));
                })
                .sorted(Comparator.comparingLong((Map<String, Object> item) -> valueAsLong(item.get("totalBps"))).reversed())
                .limit(safeLimit)
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", items);
        payload.put("freshness", buildFreshness(MikrotikMonitoringManager.MODULE_INTERFACE_TRAFFIC));
        return payload;
    }

    public Map<String, Object> getTrafficTimeline(int minutes) {
        return getTrafficTimeline(minutes, null);
    }

    public Map<String, Object> getTrafficTimeline(int minutes, Collection<Long> allowedDeviceIds) {
        int safeMinutes = Math.max(minutes, 15);
        int bucketSizeMinutes = safeMinutes <= 120 ? 5 : 15;
        LocalDateTime end = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime start = end.minusMinutes(safeMinutes);
        List<MikrotikInterfaceTraffic> rows = mikrotikInterfaceTrafficRepository.findRecentWithDeviceAndInterface(start).stream()
                .filter(row -> matchesDeviceId(allowedDeviceIds, row.getDevice().getId()))
                .toList();

        Map<LocalDateTime, long[]> buckets = new LinkedHashMap<>();
        for (LocalDateTime cursor = start; !cursor.isAfter(end); cursor = cursor.plusMinutes(bucketSizeMinutes)) {
            buckets.put(cursor, new long[]{0L, 0L, 0L});
        }

        for (MikrotikInterfaceTraffic row : rows) {
            if (row.getCollectedAt() == null) {
                continue;
            }
            LocalDateTime bucketKey = row.getCollectedAt()
                    .truncatedTo(ChronoUnit.MINUTES)
                    .minusMinutes(row.getCollectedAt().getMinute() % bucketSizeMinutes);
            long[] bucket = buckets.computeIfAbsent(bucketKey, ignored -> new long[]{0L, 0L, 0L});
            bucket[0] += valueAsLong(row.getInBps());
            bucket[1] += valueAsLong(row.getOutBps());
            bucket[2]++;
        }

        List<Map<String, Object>> history = new ArrayList<>();
        Map<String, Object> peakWindow = null;
        for (Map.Entry<LocalDateTime, long[]> entry : buckets.entrySet()) {
            long count = Math.max(entry.getValue()[2], 1L);
            long rxBps = entry.getValue()[0] / count;
            long txBps = entry.getValue()[1] / count;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("capturedAt", entry.getKey());
            item.put("rxBps", rxBps);
            item.put("txBps", txBps);
            item.put("rxMbps", toMbps(rxBps));
            item.put("txMbps", toMbps(txBps));
            item.put("totalBps", rxBps + txBps);
            item.put("totalMbps", toMbps(rxBps + txBps));
            history.add(item);
            if (peakWindow == null || valueAsLong(item.get("totalBps")) > valueAsLong(peakWindow.get("totalBps"))) {
                peakWindow = item;
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("minutes", safeMinutes);
        payload.put("bucketSizeMinutes", bucketSizeMinutes);
        payload.put("items", history);
        payload.put("peakWindow", peakWindow);
        payload.put("freshness", buildFreshness(MikrotikMonitoringManager.MODULE_INTERFACE_TRAFFIC));
        return payload;
    }

    public Map<String, Object> getInterfaces(int page, int size, Long deviceId, String status, String search) {
        List<Map<String, Object>> filtered = latestInterfaceRows(deviceId, null).stream()
                .filter(row -> status == null || status.isBlank() || status.equalsIgnoreCase(String.valueOf(row.get("status"))))
                .filter(row -> matchesSearch(search, String.valueOf(row.get("interfaceName")), String.valueOf(row.get("deviceName"))))
                .toList();
        return pagePayload(filtered, page, size, buildFreshness(MikrotikMonitoringManager.MODULE_INTERFACE_TRAFFIC));
    }

    public Map<String, Object> getInterfaceDetail(Long interfaceId, int historyMinutes) {
        MikrotikInterface iface = mikrotikInterfaceRepository.findById(interfaceId)
                .orElseThrow(() -> new IllegalArgumentException("Interface tidak ditemukan"));
        MikrotikInterfaceTraffic latest = mikrotikInterfaceTrafficRepository
                .findTopByMikrotikInterfaceIdOrderByCollectedAtDesc(interfaceId)
                .orElse(null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", iface.getId());
        payload.put("deviceId", iface.getDevice().getId());
        payload.put("deviceName", iface.getDevice().resolveDeviceName());
        payload.put("interfaceName", iface.getInterfaceName());
        payload.put("interfaceType", iface.getInterfaceType());
        payload.put("comment", iface.getComment());
        payload.put("status", iface.getOperStatus());
        payload.put("lastSeen", iface.getLastSeenAt());
        if (latest != null) {
            Map<String, Object> latestPayload = new LinkedHashMap<>();
            latestPayload.put("rxBps", latest.getInBps());
            latestPayload.put("txBps", latest.getOutBps());
            latestPayload.put("capturedAt", latest.getCollectedAt());
            payload.put("latest", latestPayload);
        } else {
            payload.put("latest", null);
        }
        payload.put("history", buildInterfaceHistory(interfaceId, historyMinutes));
        payload.put("freshness", buildFreshness(MikrotikMonitoringManager.MODULE_INTERFACE_TRAFFIC));
        return payload;
    }

    public Map<String, Object> getInterfaceHistory(Long interfaceId, int minutes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("interfaceId", interfaceId);
        payload.put("history", buildInterfaceHistory(interfaceId, minutes));
        payload.put("freshness", buildFreshness(MikrotikMonitoringManager.MODULE_INTERFACE_TRAFFIC));
        return payload;
    }

    public Map<String, Object> getPppoeActiveSummary() {
        return getPppoeActiveSummary(null);
    }

    public Map<String, Object> getPppoeActiveSummary(Collection<Long> allowedDeviceIds) {
        List<MikrotikPppoeSession> activeSessions = mikrotikPppoeSessionRepository.findByStatusOrderByLastSyncAtDesc("active").stream()
                .filter(session -> matchesDeviceId(allowedDeviceIds, session.getDevice().getId()))
                .toList();
        Map<String, Long> byProfile = activeSessions.stream()
                .collect(Collectors.groupingBy(session -> safeText(session.getProfileName(), "unprofiled"), LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> byDevice = activeSessions.stream()
                .collect(Collectors.groupingBy(session -> safeText(session.getDevice().resolveDeviceName(), "unknown"), LinkedHashMap::new, Collectors.counting()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalActive", activeSessions.size());
        payload.put("byProfile", byProfile);
        payload.put("byDevice", byDevice);
        payload.put("topProfiles", toTopCountItems(byProfile, 5, "profile"));
        payload.put("topDevices", toTopCountItems(byDevice, 5, "deviceName"));
        payload.put("latestActiveUsers", activeSessions.stream()
                .limit(8)
                .map(this::mapSession)
                .toList());
        payload.put("freshness", buildFreshness(MikrotikMonitoringManager.MODULE_PPP_ACTIVE));
        return payload;
    }

    public Map<String, Object> getPppoeActive(int page, int size, Long deviceId, String profile, String status, String search) {
        return getPppoeActive(page, size, deviceId, profile, status, search, null);
    }

    public Map<String, Object> getPppoeActive(int page, int size, Long deviceId, String profile, String status, String search, Collection<Long> allowedDeviceIds) {
        List<MikrotikPppoeSession> filtered = loadPppoeSessions(deviceId, allowedDeviceIds).stream()
                .filter(session -> matchesEquals(profile, session.getProfileName()))
                .filter(session -> matchesEquals(status, session.getStatus()))
                .filter(session -> matchesSearch(search, session.getUsername(), session.getIpAddress(), session.getCallerId()))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        applyPage(payload, filtered.stream().map(this::mapSession).toList(), page, size);
        payload.put("freshness", buildFreshness(MikrotikMonitoringManager.MODULE_PPP_ACTIVE));
        return payload;
    }

    public Map<String, Object> getPppoeActiveDetail(Long id) {
        MikrotikPppoeSession session = mikrotikPppoeSessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Session tidak ditemukan"));
        Map<String, Object> payload = mapSession(session);
        payload.put("freshness", buildFreshness(MikrotikMonitoringManager.MODULE_PPP_ACTIVE));
        return payload;
    }

    public List<String> getPppoeProfiles() {
        return getPppoeProfiles(null);
    }

    public List<String> getPppoeProfiles(Collection<Long> allowedDeviceIds) {
        return loadPppoeSessions(null, allowedDeviceIds).stream()
                .map(MikrotikPppoeSession::getProfileName)
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public List<Map<String, Object>> getPppoeDevices() {
        return getPppoeDevices(null);
    }

    public List<Map<String, Object>> getPppoeDevices(Collection<Long> allowedDeviceIds) {
        return mikrotikDeviceRepository.findByIsActiveTrue().stream()
                .filter(device -> matchesDeviceId(allowedDeviceIds, device.getId()))
                .map(device -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", device.getId());
                    item.put("name", safeText(device.resolveDeviceName(), "unknown"));
                    return item;
                })
                .toList();
    }

    public Map<String, Object> getPppoeEventSummary() {
        return getPppoeEventSummary(null);
    }

    public Map<String, Object> getPppoeEventSummary(Collection<Long> allowedDeviceIds) {
        List<MikrotikPppoeEvent> recentEvents = loadPppoeEvents(null, allowedDeviceIds).stream()
                .limit(200)
                .toList();
        Map<String, Long> byType = recentEvents.stream()
                .collect(Collectors.groupingBy(event -> safeText(event.getEventType(), "unknown"), LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> bySeverity = recentEvents.stream()
                .collect(Collectors.groupingBy(event -> safeText(event.getSeverity(), "info"), LinkedHashMap::new, Collectors.counting()));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalRecent", recentEvents.size());
        payload.put("byType", byType);
        payload.put("bySeverity", bySeverity);
        payload.put("authFailedCount", byType.getOrDefault("auth_failed", 0L));
        payload.put("criticalCount", bySeverity.getOrDefault("critical", 0L));
        payload.put("latestEvents", recentEvents.stream()
                .limit(8)
                .map(this::mapEvent)
                .toList());
        payload.put("freshness", buildFreshness(MikrotikMonitoringManager.MODULE_PPP_EVENTS));
        return payload;
    }

    public Map<String, Object> getPppoeEvents(int page, int size, Long deviceId, String eventType, String username, String search) {
        return getPppoeEvents(page, size, deviceId, eventType, username, search, null);
    }

    public Map<String, Object> getPppoeEvents(int page, int size, Long deviceId, String eventType, String username, String search, Collection<Long> allowedDeviceIds) {
        List<MikrotikPppoeEvent> filtered = loadPppoeEvents(deviceId, allowedDeviceIds).stream()
                .filter(event -> matchesEquals(eventType, event.getEventType()))
                .filter(event -> matchesSearch(username, event.getUsername()))
                .filter(event -> matchesSearch(search, event.getRawMessage(), event.getUsername(), event.getIpAddress(), event.getCallerId()))
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        applyPage(payload, filtered.stream().map(this::mapEvent).toList(), page, size);
        payload.put("freshness", buildFreshness(MikrotikMonitoringManager.MODULE_PPP_EVENTS));
        return payload;
    }

    public Map<String, Object> getPppoeEventDetail(Long id) {
        MikrotikPppoeEvent event = mikrotikPppoeEventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Event tidak ditemukan"));
        Map<String, Object> payload = mapEvent(event);
        payload.put("freshness", buildFreshness(MikrotikMonitoringManager.MODULE_PPP_EVENTS));
        return payload;
    }

    public List<String> getPppoeEventTypes() {
        return getPppoeEventTypes(null);
    }

    public List<String> getPppoeEventTypes(Collection<Long> allowedDeviceIds) {
        return loadPppoeEvents(null, allowedDeviceIds).stream()
                .map(MikrotikPppoeEvent::getEventType)
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public void pruneOldData() {
        mikrotikInterfaceTrafficRepository.deleteByCollectedAtBefore(LocalDateTime.now().minusDays(7));
        mikrotikPppoeEventRepository.deleteByCreatedAtBefore(LocalDateTime.now().minusDays(30));
    }

    private List<Map<String, Object>> buildInterfaceHistory(Long interfaceId, int minutes) {
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusMinutes(Math.max(minutes, 10));
        List<MikrotikInterfaceTraffic> rows = mikrotikInterfaceTrafficRepository
                .findByMikrotikInterfaceIdAndCollectedAtBetweenOrderByCollectedAtAsc(interfaceId, start, end);

        Map<LocalDateTime, long[]> buckets = new LinkedHashMap<>();
        for (MikrotikInterfaceTraffic row : rows) {
            LocalDateTime bucket = row.getCollectedAt().truncatedTo(ChronoUnit.MINUTES);
            long[] values = buckets.computeIfAbsent(bucket, ignored -> new long[]{0L, 0L, 0L});
            values[0] += valueAsLong(row.getInBps());
            values[1] += valueAsLong(row.getOutBps());
            values[2]++;
        }

        List<Map<String, Object>> history = new ArrayList<>();
        for (Map.Entry<LocalDateTime, long[]> entry : buckets.entrySet()) {
            long count = Math.max(entry.getValue()[2], 1L);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("capturedAt", entry.getKey());
            item.put("rxBps", entry.getValue()[0] / count);
            item.put("txBps", entry.getValue()[1] / count);
            item.put("rxMbps", toMbps(entry.getValue()[0] / count));
            item.put("txMbps", toMbps(entry.getValue()[1] / count));
            history.add(item);
        }
        return history;
    }

    private List<Map<String, Object>> latestInterfaceRows(Long deviceId, Collection<Long> allowedDeviceIds) {
        List<MikrotikInterfaceTraffic> recent = mikrotikInterfaceTrafficRepository.findLatestMonitoredTraffic(PageRequest.of(0, 1000));
        Map<Long, MikrotikInterfaceTraffic> latestByInterface = new LinkedHashMap<>();
        for (MikrotikInterfaceTraffic row : recent) {
            Long id = row.getMikrotikInterface().getId();
            if (deviceId != null && !deviceId.equals(row.getDevice().getId())) {
                continue;
            }
            if (!matchesDeviceId(allowedDeviceIds, row.getDevice().getId())) {
                continue;
            }
            latestByInterface.putIfAbsent(id, row);
        }
        return latestByInterface.values().stream()
                .sorted(Comparator.comparingLong((MikrotikInterfaceTraffic row) -> valueAsLong(row.getInBps()) + valueAsLong(row.getOutBps())).reversed())
                .map(row -> {
                    MikrotikInterface iface = row.getMikrotikInterface();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", iface.getId());
                    item.put("deviceId", row.getDevice().getId());
                    item.put("deviceName", row.getDevice().resolveDeviceName());
                    item.put("interfaceName", iface.getInterfaceName());
                    item.put("interfaceType", iface.getInterfaceType());
                    item.put("comment", iface.getComment());
                    item.put("status", iface.getOperStatus());
                    item.put("rxBps", valueAsLong(row.getInBps()));
                    item.put("txBps", valueAsLong(row.getOutBps()));
                    item.put("rxMbps", toMbps(valueAsLong(row.getInBps())));
                    item.put("txMbps", toMbps(valueAsLong(row.getOutBps())));
                    item.put("capturedAt", row.getCollectedAt());
                    return item;
                })
                .toList();
    }

    private Map<String, Object> mapSession(MikrotikPppoeSession session) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", session.getId());
        item.put("deviceId", session.getDevice().getId());
        item.put("deviceName", session.getDevice().resolveDeviceName());
        item.put("username", session.getUsername());
        item.put("ipAddress", session.getIpAddress());
        item.put("callerId", session.getCallerId());
        item.put("profile", session.getProfileName());
        item.put("service", session.getService());
        item.put("status", session.getStatus());
        item.put("loginAt", session.getLoginAt());
        item.put("logoutAt", session.getLogoutAt());
        item.put("lastSeen", session.getLastSeen());
        item.put("syncedAt", session.getSyncedAt());
        return item;
    }

    private Map<String, Object> mapEvent(MikrotikPppoeEvent event) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", event.getId());
        item.put("deviceId", event.getDevice().getId());
        item.put("deviceName", event.getDevice().resolveDeviceName());
        item.put("username", event.getUsername());
        item.put("eventType", event.getEventType());
        item.put("ipAddress", event.getIpAddress());
        item.put("callerId", event.getCallerId());
        item.put("profile", event.getProfile());
        item.put("severity", event.getSeverity());
        item.put("rawMessage", event.getRawMessage());
        item.put("eventTime", event.getEventTime());
        item.put("syncedAt", event.getSyncedAt());
        return item;
    }

    private Map<String, Object> buildFreshness(String moduleName) {
        List<DeviceSyncStatus> statuses = deviceSyncStatusRepository.findByModuleName(moduleName);
        LocalDateTime latestSuccess = statuses.stream()
                .map(DeviceSyncStatus::getLastSuccessAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        long staleDevices = statuses.stream()
                .filter(status -> isFreshness(status, "stale"))
                .count();
        long delayedDevices = statuses.stream()
                .filter(status -> isFreshness(status, "delayed"))
                .count();
        long unreachableDevices = statuses.stream()
                .filter(status -> isFreshness(status, "unreachable"))
                .count();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("module", moduleName);
        payload.put("lastSyncAt", latestSuccess);
        payload.put("status", summarizeFreshness(staleDevices, delayedDevices, unreachableDevices));
        payload.put("staleDevices", staleDevices);
        payload.put("delayedDevices", delayedDevices);
        payload.put("unreachableDevices", unreachableDevices);
        payload.put("devices", statuses.stream().map(status -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("deviceId", status.getDevice().getId());
            item.put("deviceName", safeText(status.getDevice().resolveDeviceName(), "unknown"));
            item.put("lastSuccessAt", status.getLastSuccessAt());
            item.put("lastAttemptAt", status.getLastAttemptAt());
            item.put("status", safeText(status.getStatus(), "idle"));
            item.put("freshnessStatus", evaluateFreshness(status));
            item.put("lastError", status.getLastError());
            item.put("nextRetryAt", status.getNextRetryAt());
            item.put("lastDurationMs", status.getLastDurationMs());
            item.put("lastItemCount", status.getLastItemCount());
            return item;
        }).toList());
        return payload;
    }

    private boolean isFreshness(DeviceSyncStatus status, String expected) {
        return expected.equalsIgnoreCase(evaluateFreshness(status));
    }

    private String evaluateFreshness(DeviceSyncStatus status) {
        if (status == null) {
            return "unreachable";
        }
        if (status.getLastSuccessAt() == null) {
            return "unreachable";
        }
        long staleAfterSeconds = status.getStaleAfterSeconds() != null && status.getStaleAfterSeconds() > 0
                ? status.getStaleAfterSeconds()
                : 300L;
        LocalDateTime now = LocalDateTime.now();
        if (status.getNextRetryAt() != null && status.getNextRetryAt().isAfter(now)
                && status.getLastSuccessAt().isBefore(now.minusSeconds(staleAfterSeconds))) {
            return "unreachable";
        }
        long ageSeconds = ChronoUnit.SECONDS.between(status.getLastSuccessAt(), now);
        if (ageSeconds > staleAfterSeconds * 2L) {
            return "stale";
        }
        if (ageSeconds > staleAfterSeconds) {
            return "delayed";
        }
        return "fresh";
    }

    private String summarizeFreshness(long staleDevices, long delayedDevices, long unreachableDevices) {
        if (unreachableDevices > 0) {
            return "unreachable";
        }
        if (staleDevices > 0) {
            return "stale";
        }
        if (delayedDevices > 0) {
            return "delayed";
        }
        return "fresh";
    }

    private List<Map<String, Object>> toTopCountItems(Map<String, Long> counts, int limit, String labelKey) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(Math.max(limit, 1))
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put(labelKey, entry.getKey());
                    item.put("count", entry.getValue());
                    return item;
                })
                .toList();
    }

    private Map<String, Object> pagePayload(List<Map<String, Object>> items, int page, int size, Map<String, Object> freshness) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int fromIndex = Math.min(safePage * safeSize, items.size());
        int toIndex = Math.min(fromIndex + safeSize, items.size());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", items.subList(fromIndex, toIndex));
        payload.put("page", safePage);
        payload.put("size", safeSize);
        payload.put("totalItems", items.size());
        payload.put("totalPages", (int) Math.ceil(items.size() / (double) safeSize));
        payload.put("freshness", freshness);
        return payload;
    }

    private void applyPage(Map<String, Object> payload, List<Map<String, Object>> items, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int fromIndex = Math.min(safePage * safeSize, items.size());
        int toIndex = Math.min(fromIndex + safeSize, items.size());
        payload.put("items", items.subList(fromIndex, toIndex));
        payload.put("page", safePage);
        payload.put("size", safeSize);
        payload.put("totalItems", items.size());
        payload.put("totalPages", (int) Math.ceil(items.size() / (double) safeSize));
    }

    private List<MikrotikPppoeSession> loadPppoeSessions(Long deviceId, Collection<Long> allowedDeviceIds) {
        return deviceId != null
                ? mikrotikPppoeSessionRepository.findAllWithDeviceByDeviceIdOrderByLastSyncAtDesc(deviceId).stream()
                        .filter(session -> matchesDeviceId(allowedDeviceIds, session.getDevice().getId()))
                        .toList()
                : mikrotikPppoeSessionRepository.findAllWithDeviceOrderByLastSyncAtDesc().stream()
                        .filter(session -> matchesDeviceId(allowedDeviceIds, session.getDevice().getId()))
                        .toList();
    }

    private List<MikrotikPppoeEvent> loadPppoeEvents(Long deviceId, Collection<Long> allowedDeviceIds) {
        return deviceId != null
                ? mikrotikPppoeEventRepository.findAllWithDeviceByDeviceIdOrderByEventTimeDesc(deviceId).stream()
                        .filter(event -> matchesDeviceId(allowedDeviceIds, event.getDevice().getId()))
                        .toList()
                : mikrotikPppoeEventRepository.findAllWithDeviceOrderByEventTimeDesc().stream()
                        .filter(event -> matchesDeviceId(allowedDeviceIds, event.getDevice().getId()))
                        .toList();
    }

    private boolean matchesDeviceId(Collection<Long> allowedDeviceIds, Long deviceId) {
        return allowedDeviceIds == null || allowedDeviceIds.isEmpty() || allowedDeviceIds.contains(deviceId);
    }

    private boolean matchesSearch(String search, String... values) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesEquals(String expected, String actual) {
        if (!hasText(expected)) {
            return true;
        }
        if (!hasText(actual)) {
            return false;
        }
        return actual.trim().equalsIgnoreCase(expected.trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeText(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private String blankToNull(String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }

    private long valueAsLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    private double toMbps(long value) {
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(1_000_000L), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
