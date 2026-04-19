package com.netmaster.nmx.service;

import com.netmaster.nmx.model.DeviceSyncStatus;
import com.netmaster.nmx.model.MikrotikDevice;
import com.netmaster.nmx.model.MikrotikDeviceMetric;
import com.netmaster.nmx.model.MikrotikInterface;
import com.netmaster.nmx.model.MikrotikInterfaceTraffic;
import com.netmaster.nmx.model.MikrotikPppoeEvent;
import com.netmaster.nmx.model.MikrotikPppoeSession;
import com.netmaster.nmx.repository.DeviceSyncStatusRepository;
import com.netmaster.nmx.repository.MikrotikDeviceMetricRepository;
import com.netmaster.nmx.repository.MikrotikDeviceRepository;
import com.netmaster.nmx.repository.MikrotikInterfaceRepository;
import com.netmaster.nmx.repository.MikrotikInterfaceTrafficRepository;
import com.netmaster.nmx.repository.MikrotikPppoeEventRepository;
import com.netmaster.nmx.repository.MikrotikPppoeSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class MikrotikMonitoringManager {

    public static final String MODULE_INTERFACE_TRAFFIC = "interface_traffic";
    public static final String MODULE_PPP_ACTIVE = "pppoe_active";
    public static final String MODULE_PPP_EVENTS = "pppoe_events";

    private final MikrotikDeviceRepository mikrotikDeviceRepository;
    private final MikrotikDeviceMetricRepository mikrotikDeviceMetricRepository;
    private final MikrotikInterfaceRepository mikrotikInterfaceRepository;
    private final MikrotikInterfaceTrafficRepository mikrotikInterfaceTrafficRepository;
    private final MikrotikPppoeSessionRepository mikrotikPppoeSessionRepository;
    private final MikrotikPppoeEventRepository mikrotikPppoeEventRepository;
    private final DeviceSyncStatusRepository deviceSyncStatusRepository;
    private final MikrotikApiService mikrotikApiService;

    @Transactional
    public void pollDueMonitoringDevices() {
        List<MikrotikDevice> dueDevices = mikrotikDeviceRepository.findByIsActiveTrue().stream()
                .filter(device -> Boolean.TRUE.equals(device.getApiEnabled()))
                .filter(this::isMonitoringDue)
                .sorted(Comparator.comparing(device -> dueAt(device.getLastSnmpSyncAt(), device.getPollingIntervalSnmp(), 15)))
                .limit(1)
                .toList();
        dueDevices.forEach(device -> runMonitoringSync(device.getId()));
    }

    @Transactional
    public void syncDueApiDevices() {
        List<MikrotikDevice> dueDevices = mikrotikDeviceRepository.findByIsActiveTrue().stream()
                .filter(device -> Boolean.TRUE.equals(device.getApiEnabled()))
                .filter(this::isApiDue)
                .sorted(Comparator.comparing(device -> dueAt(device.getLastApiSyncAt(), device.getSyncIntervalApi(), 30)))
                .limit(1)
                .toList();
        dueDevices.forEach(device -> runApiSync(device.getId()));
    }

    @Transactional
    public void syncNow(Long deviceId) {
        runMonitoringSync(deviceId);
        runApiSync(deviceId);
    }

    @Transactional
    public void runMonitoringSync(Long deviceId) {
        MikrotikDevice device = getDevice(deviceId);
        DeviceSyncStatus syncStatus = beginAttempt(device, MODULE_INTERFACE_TRAFFIC, resolveTrafficInterval(device));
        if (shouldPause(syncStatus)) {
            return;
        }
        long startedAt = System.currentTimeMillis();
        try {
            MikrotikApiService.MonitoringSnapshot snapshot = executeWithRetry(() -> mikrotikApiService.collectMonitoringSnapshot(device));
            applyMonitoringSnapshot(device, snapshot);
            completeSuccess(syncStatus, startedAt, snapshot.interfaces().size());
        } catch (Exception ex) {
            log.warn("Monitoring sync gagal untuk device {}: {}", device.resolveDeviceName(), ex.getMessage());
            device.applyCurrentStatus("offline");
            mikrotikDeviceRepository.save(device);
            completeFailure(syncStatus, startedAt, ex);
        }
    }

    @Transactional
    public void runApiSync(Long deviceId) {
        MikrotikDevice device = getDevice(deviceId);
        DeviceSyncStatus activeStatus = beginAttempt(device, MODULE_PPP_ACTIVE, resolvePppInterval(device));
        DeviceSyncStatus eventStatus = beginAttempt(device, MODULE_PPP_EVENTS, resolveEventInterval(device));
        if (shouldPause(activeStatus) || shouldPause(eventStatus)) {
            return;
        }
        long startedAt = System.currentTimeMillis();
        try {
            MikrotikApiService.PppSyncSnapshot snapshot = executeWithRetry(() -> mikrotikApiService.collectPppSnapshot(device));
            int activeCount = applyApiSnapshot(device, snapshot);
            int eventCount = importPppLogs(device, snapshot);
            device.setLastApiSyncAt(snapshot.collectedAt());
            mikrotikDeviceRepository.save(device);
            completeSuccess(activeStatus, startedAt, activeCount);
            completeSuccess(eventStatus, startedAt, eventCount);
        } catch (Exception ex) {
            log.warn("PPPoE sync gagal untuk device {}: {}", device.resolveDeviceName(), ex.getMessage());
            completeFailure(activeStatus, startedAt, ex);
            completeFailure(eventStatus, startedAt, ex);
        }
    }

    public Map<String, Object> buildSummary() {
        List<MikrotikDevice> devices = mikrotikDeviceRepository.findByIsActiveTrue();
        long online = devices.stream().filter(device -> "online".equalsIgnoreCase(device.resolveCurrentStatus())).count();
        long offline = devices.size() - online;
        LocalDateTime lastUpdate = devices.stream()
                .map(device -> latest(device.getLastSnmpSyncAt(), device.getLastApiSyncAt()))
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalDevice", devices.size());
        payload.put("onlineDevice", online);
        payload.put("offlineDevice", offline);
        payload.put("lastUpdate", lastUpdate);
        payload.put("deviceFlow", "mikrotik -> worker -> parser -> db/cache -> internal api");
        payload.put("pppFlow", "mikrotik -> ppp worker -> dedup -> db/cache -> internal api");
        payload.put("source", "internal cache");
        return payload;
    }

    private void applyMonitoringSnapshot(MikrotikDevice device, MikrotikApiService.MonitoringSnapshot snapshot) {
        device.applyDeviceName(firstText(snapshot.systemName(), device.resolveDeviceName(), device.getIpAddress()));
        device.applyCurrentStatus(snapshot.reachable() ? "online" : "offline");
        device.setCpuLoad(snapshot.cpuLoad());
        device.setMemoryTotal(snapshot.memoryTotal());
        device.setMemoryUsed(snapshot.memoryUsed());
        device.setUptimeSeconds(snapshot.uptimeSeconds());
        device.setRosVersion(snapshot.routerOsVersion());
        device.setRouterboardVersion(snapshot.boardName());
        device.setLastSeenAt(snapshot.collectedAt());
        device.setLastMonitored(snapshot.collectedAt());
        device.setLastSnmpSyncAt(snapshot.collectedAt());
        mikrotikDeviceRepository.save(device);

        mikrotikDeviceMetricRepository.save(MikrotikDeviceMetric.builder()
                .device(device)
                .cpuLoad(snapshot.cpuLoad())
                .memoryTotal(snapshot.memoryTotal())
                .memoryUsed(snapshot.memoryUsed())
                .memoryFree(snapshot.memoryFree())
                .uptime(snapshot.uptimeSeconds())
                .temperature(snapshot.temperature())
                .voltage(snapshot.voltage())
                .boardHealth(snapshot.boardHealth())
                .source("api")
                .collectedAt(snapshot.collectedAt())
                .build());

        for (MikrotikApiService.MonitoringInterfaceSnapshot interfaceSnapshot : snapshot.interfaces()) {
            MikrotikInterface mikrotikInterface = mikrotikInterfaceRepository
                    .findByDeviceIdAndInterfaceIndex(device.getId(), interfaceSnapshot.interfaceIndex())
                    .orElseGet(() -> MikrotikInterface.builder()
                            .device(device)
                            .interfaceIndex(interfaceSnapshot.interfaceIndex())
                            .build());

            mikrotikInterface.setInterfaceName(interfaceSnapshot.interfaceName());
            mikrotikInterface.setInterfaceType(interfaceSnapshot.interfaceType());
            mikrotikInterface.setComment(interfaceSnapshot.comment());
            mikrotikInterface.setAdminStatus(interfaceSnapshot.adminStatus());
            mikrotikInterface.setOperStatus(interfaceSnapshot.operStatus());
            mikrotikInterface.setLastSeenAt(snapshot.collectedAt());
            mikrotikInterface = mikrotikInterfaceRepository.save(mikrotikInterface);

            MikrotikInterfaceTraffic previous = mikrotikInterfaceTrafficRepository
                    .findTopByMikrotikInterfaceIdOrderByCollectedAtDesc(mikrotikInterface.getId())
                    .orElse(null);

            long inBps = calculateBps(previous != null ? previous.getInOctets() : null, interfaceSnapshot.inOctets(),
                    previous != null ? previous.getCollectedAt() : null, snapshot.collectedAt());
            long outBps = calculateBps(previous != null ? previous.getOutOctets() : null, interfaceSnapshot.outOctets(),
                    previous != null ? previous.getCollectedAt() : null, snapshot.collectedAt());

            mikrotikInterfaceTrafficRepository.save(MikrotikInterfaceTraffic.builder()
                    .device(device)
                    .mikrotikInterface(mikrotikInterface)
                    .inOctets(interfaceSnapshot.inOctets())
                    .outOctets(interfaceSnapshot.outOctets())
                    .inBps(Math.max(inBps, 0L))
                    .outBps(Math.max(outBps, 0L))
                    .source("api")
                    .collectedAt(snapshot.collectedAt())
                    .build());
        }
    }

    private int applyApiSnapshot(MikrotikDevice device, MikrotikApiService.PppSyncSnapshot snapshot) {
        Map<String, MikrotikPppoeSession> existingSessions = new LinkedHashMap<>();
        for (MikrotikPppoeSession session : mikrotikPppoeSessionRepository.findByDeviceIdOrderByLastSyncAtDesc(device.getId())) {
            existingSessions.putIfAbsent(session.getUsername(), session);
        }

        Set<String> activeUsernames = new LinkedHashSet<>();
        int updatedCount = 0;
        for (MikrotikRouterOsApiClient.MikrotikPppSessionSnapshot activeSession : snapshot.activeSessions()) {
            activeUsernames.add(activeSession.username());
            MikrotikPppoeSession session = existingSessions.getOrDefault(activeSession.username(), new MikrotikPppoeSession());
            boolean newLogin = session.getId() == null || !"active".equalsIgnoreCase(session.getStatus());

            session.setDevice(device);
            session.setUsername(activeSession.username());
            session.setIpAddress(activeSession.ipAddress());
            session.setCallerId(activeSession.callerId());
            session.setSessionId(activeSession.sessionId());
            session.setProfileName(firstText(snapshot.secretProfiles().get(activeSession.username()), activeSession.profileName()));
            session.setService(activeSession.profileName());
            session.setStatus("active");
            session.setLastSeen(snapshot.collectedAt());
            session.setLastSyncAt(snapshot.collectedAt());
            session.setSyncedAt(snapshot.collectedAt());
            session.setSource("api");
            if (session.getLoginAt() == null && activeSession.uptimeSeconds() != null) {
                session.setLoginAt(snapshot.collectedAt().minusSeconds(activeSession.uptimeSeconds()));
            } else if (session.getLoginAt() == null) {
                session.setLoginAt(snapshot.collectedAt());
            }
            session.setLogoutAt(null);
            MikrotikPppoeSession saved = mikrotikPppoeSessionRepository.save(session);
            updatedCount++;

            if (newLogin) {
                saveEvent(device, saved.getUsername(), "login_success", saved.getIpAddress(), saved.getCallerId(),
                        saved.getProfileName(), "info", activeSession.rawPayload(), activeSession.rawPayload(), saved.getLoginAt());
            }
        }

        for (MikrotikPppoeSession session : existingSessions.values()) {
            if (!activeUsernames.contains(session.getUsername()) && !"logout".equalsIgnoreCase(session.getStatus())) {
                session.setStatus("logout");
                session.setLogoutAt(snapshot.collectedAt());
                session.setLastSyncAt(snapshot.collectedAt());
                session.setSyncedAt(snapshot.collectedAt());
                mikrotikPppoeSessionRepository.save(session);
                updatedCount++;
                saveEvent(device, session.getUsername(), "logout", session.getIpAddress(), session.getCallerId(),
                        session.getProfileName(), "info", "{status=logout}", "session logout", snapshot.collectedAt());
            }
        }
        return updatedCount;
    }

    private int importPppLogs(MikrotikDevice device, MikrotikApiService.PppSyncSnapshot snapshot) {
        int inserted = 0;
        for (MikrotikRouterOsApiClient.MikrotikLogSnapshot logSnapshot : snapshot.recentLogs()) {
            PppLogInfo parsed = parsePppLog(logSnapshot.message());
            String fingerprint = fingerprint(device.getId(), logSnapshot.eventTime(), logSnapshot.message());
            if (mikrotikPppoeEventRepository.findByFingerprintHash(fingerprint).isPresent()) {
                continue;
            }
            mikrotikPppoeEventRepository.save(MikrotikPppoeEvent.builder()
                    .device(device)
                    .username(parsed.username())
                    .eventType(parsed.eventType())
                    .ipAddress(parsed.ipAddress())
                    .callerId(parsed.callerId())
                    .profile(parsed.profile())
                    .severity(parsed.severity())
                    .rawPayload(logSnapshot.rawPayload())
                    .rawMessage(logSnapshot.message())
                    .eventTime(logSnapshot.eventTime())
                    .fingerprintHash(fingerprint)
                    .syncedAt(snapshot.collectedAt())
                    .source("api")
                    .build());
            inserted++;
        }
        return inserted;
    }

    private void saveEvent(MikrotikDevice device,
                           String username,
                           String eventType,
                           String ipAddress,
                           String callerId,
                           String profile,
                           String severity,
                           String rawPayload,
                           String rawMessage,
                           LocalDateTime eventTime) {
        LocalDateTime resolvedEventTime = eventTime != null ? eventTime : LocalDateTime.now();
        String fingerprint = fingerprint(device.getId(), resolvedEventTime, eventType + "|" + username + "|" + ipAddress + "|" + rawMessage);
        if (mikrotikPppoeEventRepository.findByFingerprintHash(fingerprint).isPresent()) {
            return;
        }
        mikrotikPppoeEventRepository.save(MikrotikPppoeEvent.builder()
                .device(device)
                .username(username)
                .eventType(eventType)
                .ipAddress(ipAddress)
                .callerId(callerId)
                .profile(profile)
                .severity(severity)
                .rawPayload(rawPayload)
                .rawMessage(rawMessage)
                .eventTime(resolvedEventTime)
                .fingerprintHash(fingerprint)
                .syncedAt(LocalDateTime.now())
                .source("api")
                .build());
    }

    private PppLogInfo parsePppLog(String message) {
        String normalized = message != null ? message.trim() : "";
        String lower = normalized.toLowerCase(Locale.ROOT);
        String eventType = "unknown_pppoe_event";
        String severity = "info";
        if (lower.contains("logged in") || lower.contains("authenticated")) {
            eventType = "login_success";
        } else if (lower.contains("logged out")) {
            eventType = "logout";
        } else if (lower.contains("authentication failed")) {
            eventType = "auth_failed";
            severity = "warning";
        } else if (lower.contains("timeout")) {
            eventType = "session_timeout";
            severity = "warning";
        } else if (lower.contains("disconnected")) {
            eventType = "disconnected";
            severity = "warning";
        }
        String username = extractQuotedOrToken(normalized, "<", ">");
        if (username == null) {
            username = extractAfter(normalized, "user ");
        }
        return new PppLogInfo(username, eventType, null, null, null, severity);
    }

    private boolean isMonitoringDue(MikrotikDevice device) {
        return isDue(device, MODULE_INTERFACE_TRAFFIC, device.getLastSnmpSyncAt(), resolveTrafficInterval(device), 15);
    }

    private boolean isApiDue(MikrotikDevice device) {
        return isDue(device, MODULE_PPP_ACTIVE, device.getLastApiSyncAt(), resolvePppInterval(device), 30)
                || isDue(device, MODULE_PPP_EVENTS, device.getLastApiSyncAt(), resolveEventInterval(device), 45);
    }

    private boolean isDue(MikrotikDevice device, String moduleName, LocalDateTime lastSyncAt, int intervalSeconds, int minimumDefault) {
        DeviceSyncStatus syncStatus = findOrCreateSyncStatus(device, moduleName, intervalSeconds);
        if (shouldPause(syncStatus)) {
            return false;
        }
        return dueAt(lastSyncAt, intervalSeconds, minimumDefault).isBefore(LocalDateTime.now());
    }

    private LocalDateTime dueAt(LocalDateTime lastSyncAt, Integer intervalSeconds, int minimumDefault) {
        if (lastSyncAt == null) {
            return LocalDateTime.MIN;
        }
        int interval = Math.max(intervalSeconds != null ? intervalSeconds : minimumDefault, minimumDefault);
        return lastSyncAt.plusSeconds(interval);
    }

    private boolean shouldPause(DeviceSyncStatus syncStatus) {
        return syncStatus.getNextRetryAt() != null && syncStatus.getNextRetryAt().isAfter(LocalDateTime.now());
    }

    private int resolveTrafficInterval(MikrotikDevice device) {
        return Math.max(device.getPollingIntervalSnmp() != null ? device.getPollingIntervalSnmp() : 15, 15);
    }

    private int resolvePppInterval(MikrotikDevice device) {
        return Math.max(device.getSyncIntervalApi() != null ? device.getSyncIntervalApi() : 30, 30);
    }

    private int resolveEventInterval(MikrotikDevice device) {
        return Math.max(resolvePppInterval(device) * 2, 45);
    }

    private long calculateBps(Long previousOctets, Long currentOctets, LocalDateTime previousTime, LocalDateTime currentTime) {
        if (previousOctets == null || currentOctets == null || previousTime == null || currentTime == null) {
            return 0L;
        }
        long diff = currentOctets - previousOctets;
        if (diff < 0L) {
            diff = currentOctets;
        }
        long seconds = Math.max(Duration.between(previousTime, currentTime).getSeconds(), 1L);
        return (diff * 8L) / seconds;
    }

    private DeviceSyncStatus beginAttempt(MikrotikDevice device, String moduleName, int staleAfterSeconds) {
        DeviceSyncStatus syncStatus = findOrCreateSyncStatus(device, moduleName, staleAfterSeconds);
        syncStatus.setLastAttemptAt(LocalDateTime.now());
        syncStatus.setStatus("running");
        return deviceSyncStatusRepository.save(syncStatus);
    }

    private void completeSuccess(DeviceSyncStatus syncStatus, long startedAt, int itemCount) {
        syncStatus.setLastSuccessAt(LocalDateTime.now());
        syncStatus.setLastError(null);
        syncStatus.setStatus("ok");
        syncStatus.setConsecutiveFailures(0);
        syncStatus.setNextRetryAt(null);
        syncStatus.setLastDurationMs(System.currentTimeMillis() - startedAt);
        syncStatus.setLastItemCount(itemCount);
        deviceSyncStatusRepository.save(syncStatus);
    }

    private void completeFailure(DeviceSyncStatus syncStatus, long startedAt, Exception ex) {
        int failures = syncStatus.getConsecutiveFailures() != null ? syncStatus.getConsecutiveFailures() + 1 : 1;
        long backoffSeconds = Math.min(30L * failures, 300L);
        syncStatus.setConsecutiveFailures(failures);
        syncStatus.setStatus("error");
        syncStatus.setLastError(trim(ex.getMessage(), 500));
        syncStatus.setLastDurationMs(System.currentTimeMillis() - startedAt);
        syncStatus.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
        deviceSyncStatusRepository.save(syncStatus);
    }

    private DeviceSyncStatus findOrCreateSyncStatus(MikrotikDevice device, String moduleName, int staleAfterSeconds) {
        return deviceSyncStatusRepository.findByDeviceIdAndModuleName(device.getId(), moduleName)
                .orElseGet(() -> DeviceSyncStatus.builder()
                        .device(device)
                        .moduleName(moduleName)
                        .staleAfterSeconds(staleAfterSeconds)
                        .status("idle")
                        .consecutiveFailures(0)
                        .build());
    }

    private String fingerprint(Long deviceId, LocalDateTime eventTime, String rawText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String source = deviceId + "|" + eventTime + "|" + rawText;
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return deviceId + ":" + eventTime + ":" + Math.abs(Objects.hash(rawText));
        }
    }

    private <T> T executeWithRetry(CheckedSupplier<T> supplier) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return supplier.get();
            } catch (Exception ex) {
                last = ex;
                try {
                    Thread.sleep((attempt + 1L) * 500L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw ex;
                }
            }
        }
        throw last;
    }

    private MikrotikDevice getDevice(Long id) {
        return mikrotikDeviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Device Mikrotik tidak ditemukan."));
    }

    private LocalDateTime latest(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String extractQuotedOrToken(String source, String prefix, String suffix) {
        if (source == null) {
            return null;
        }
        int start = source.indexOf(prefix);
        int end = source.indexOf(suffix, start + prefix.length());
        if (start >= 0 && end > start) {
            return trim(source.substring(start + prefix.length(), end), 100);
        }
        return null;
    }

    private String extractAfter(String source, String marker) {
        if (source == null || marker == null) {
            return null;
        }
        String lower = source.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(marker.toLowerCase(Locale.ROOT));
        if (index < 0) {
            return null;
        }
        String tail = source.substring(index + marker.length()).trim();
        int separator = tail.indexOf(' ');
        return trim(separator > 0 ? tail.substring(0, separator) : tail, 100);
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private record PppLogInfo(
            String username,
            String eventType,
            String ipAddress,
            String callerId,
            String profile,
            String severity
    ) {
    }
}
