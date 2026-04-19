package com.netmaster.nmx.service;

import com.netmaster.nmx.model.MikrotikDevice;
import com.netmaster.nmx.repository.RouterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class MikrotikMonitoringSnapshotService {

    private static final Duration ROUTER_LEASE = Duration.ofSeconds(65);
    private static final Duration MODAL_LEASE = Duration.ofSeconds(30);
    private static final Duration SNAPSHOT_TTL = Duration.ofSeconds(8);
    private static final int ETHER_HISTORY_LIMIT = 60;
    private static final int USER_HISTORY_LIMIT = 40;
    private static final int LOG_LIMIT = 80;

    private final RouterRepository routerRepository;
    private final MikrotikConnectionService mikrotikConnectionService;
    private final MikrotikRouterOsApiClient mikrotikRouterOsApiClient;

    private final ConcurrentMap<Long, RouterRuntimeState> stateByRouterId = new ConcurrentHashMap<>();

    public RouterListResponse getRouters() {
        List<MikrotikDevice> routers = routerRepository.findActiveRoutersOrdered();
        Long defaultRouterId = routers.isEmpty() ? null : routers.getFirst().getId();
        List<RouterOption> items = routers.stream()
                .map(this::toRouterOption)
                .toList();
        return new RouterListResponse(
                items,
                items.size(),
                items.size() > 1,
                defaultRouterId,
                items.size() == 1 ? defaultRouterId : null
        );
    }

    public MonitoringSummaryResponse getSummary(Long requestedRouterId) {
        RouterSelection selection = resolveSelection(requestedRouterId);
        if (selection.selectedRouter() == null) {
            return MonitoringSummaryResponse.empty(selection.meta(), pollingProfile());
        }

        touchRouter(selection.selectedRouter().getId());
        RouterRuntimeState state = stateByRouterId.computeIfAbsent(selection.selectedRouter().getId(), ignored -> new RouterRuntimeState());
        ensureSnapshot(selection.selectedRouter(), state);

        RouterSummary summary = buildRouterSummary(selection.selectedRouter(), state);
        return new MonitoringSummaryResponse(selection.meta(), summary, pollingProfile());
    }

    public TrafficResponse getEther1Traffic(Long requestedRouterId) {
        MonitoringSummaryResponse summary = getSummary(requestedRouterId);
        RouterSummary router = summary.router();
        return router == null
                ? new TrafficResponse(null, List.of(), StateInfo.empty("Belum ada router aktif"))
                : new TrafficResponse(router.ether1(), router.ether1() != null ? router.ether1().history() : List.of(), router.state());
    }

    public ResourceResponse getResources(Long requestedRouterId) {
        MonitoringSummaryResponse summary = getSummary(requestedRouterId);
        RouterSummary router = summary.router();
        return router == null
                ? new ResourceResponse(null, StateInfo.empty("Belum ada router aktif"))
                : new ResourceResponse(router.resources(), router.state());
    }

    public PppoeListResponse getPppoe(Long requestedRouterId) {
        MonitoringSummaryResponse summary = getSummary(requestedRouterId);
        RouterSummary router = summary.router();
        return router == null
                ? new PppoeListResponse(List.of(), 0, StateInfo.empty("Belum ada router aktif"))
                : new PppoeListResponse(router.pppoe(), router.pppoe() != null ? router.pppoe().size() : 0, router.state());
    }

    public LogResponse getLogs(Long requestedRouterId) {
        MonitoringSummaryResponse summary = getSummary(requestedRouterId);
        RouterSummary router = summary.router();
        return router == null
                ? new LogResponse(List.of(), StateInfo.empty("Belum ada router aktif"))
                : new LogResponse(router.logs(), router.state());
    }

    public UserTrafficResponse getPppoeUserTraffic(Long requestedRouterId, String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username PPPoE wajib diisi");
        }

        RouterSelection selection = resolveSelection(requestedRouterId);
        if (selection.selectedRouter() == null) {
            return UserTrafficResponse.empty(username, StateInfo.empty("Belum ada router aktif"));
        }

        touchRouter(selection.selectedRouter().getId());
        RouterRuntimeState state = stateByRouterId.computeIfAbsent(selection.selectedRouter().getId(), ignored -> new RouterRuntimeState());
        state.watchers.put(normalizeKey(username), LocalDateTime.now().plus(MODAL_LEASE));
        ensureSnapshot(selection.selectedRouter(), state);

        return buildUserTrafficResponse(selection.selectedRouter(), state, username);
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 10000)
    void pollActiveRouters() {
        LocalDateTime now = LocalDateTime.now();
        List<MikrotikDevice> activeRouters = routerRepository.findActiveRoutersOrdered();
        if (activeRouters.isEmpty()) {
            stateByRouterId.clear();
            return;
        }

        boolean singleRouterMode = activeRouters.size() == 1;
        LinkedHashSet<Long> activeIds = new LinkedHashSet<>();
        if (singleRouterMode) {
            activeIds.add(activeRouters.getFirst().getId());
        }

        for (Map.Entry<Long, RouterRuntimeState> entry : stateByRouterId.entrySet()) {
            RouterRuntimeState state = entry.getValue();
            pruneWatchers(state, now);
            if (state.leaseUntil != null && state.leaseUntil.isAfter(now)) {
                activeIds.add(entry.getKey());
            }
        }

        for (MikrotikDevice router : activeRouters) {
            if (!activeIds.contains(router.getId())) {
                continue;
            }
            RouterRuntimeState state = stateByRouterId.computeIfAbsent(router.getId(), ignored -> new RouterRuntimeState());
            if (state.lastSuccessAt != null && state.lastSuccessAt.plus(SNAPSHOT_TTL).isAfter(now)) {
                continue;
            }
            if (state.circuitOpenUntil != null && state.circuitOpenUntil.isAfter(now)) {
                continue;
            }
            refreshRouter(router, state);
        }
    }

    private void ensureSnapshot(MikrotikDevice router, RouterRuntimeState state) {
        LocalDateTime now = LocalDateTime.now();
        pruneWatchers(state, now);
        if (state.lastSuccessAt != null && state.lastSuccessAt.plus(SNAPSHOT_TTL).isAfter(now)) {
            return;
        }
        if (state.circuitOpenUntil != null && state.circuitOpenUntil.isAfter(now)) {
            return;
        }
        refreshRouter(router, state);
    }

    private synchronized void refreshRouter(MikrotikDevice router, RouterRuntimeState state) {
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            MikrotikConnectionService.ConnectionTarget target = resolveTarget(router);
            MikrotikRouterOsApiClient.MikrotikMonitoringBundle bundle = mikrotikRouterOsApiClient.collectMonitoringBundle(
                    target,
                    router.resolveApiUsername(),
                    router.resolveApiPassword(),
                    "ether1",
                    50
            );

            RouterSnapshot snapshot = buildSnapshot(router, state, bundle);
            state.snapshot = snapshot;
            state.lastSuccessAt = LocalDateTime.now();
            state.lastError = null;
            state.failureCount = 0;
            state.circuitOpenUntil = null;
            state.lastDurationMs = Duration.between(startedAt, state.lastSuccessAt).toMillis();
        } catch (Exception ex) {
            state.failureCount += 1;
            state.lastError = ex.getMessage();
            state.lastFailureAt = LocalDateTime.now();
            state.lastDurationMs = Duration.between(startedAt, state.lastFailureAt).toMillis();
            long backoffSeconds = Math.min(60, (long) Math.pow(2, Math.min(state.failureCount, 5)) * 3L);
            state.circuitOpenUntil = state.lastFailureAt.plusSeconds(backoffSeconds);
            log.warn("Monitoring snapshot failed for router {}: {}", router.getId(), ex.getMessage());
        }
    }

    private RouterSnapshot buildSnapshot(MikrotikDevice router,
                                         RouterRuntimeState state,
                                         MikrotikRouterOsApiClient.MikrotikMonitoringBundle bundle) {
        LocalDateTime now = bundle.collectedAt();

        ResourceSnapshot resourceSnapshot = new ResourceSnapshot(
                valueText(bundle.identityName(), router.resolveDeviceName()),
                valueText(bundle.routerOsVersion(), router.getRosVersion()),
                valueText(bundle.boardName(), router.getRouterboardVersion()),
                valueText(bundle.architectureName(), null),
                bundle.uptimeSeconds(),
                bundle.cpuLoad(),
                positiveOrNull(bundle.cpuCount()),
                positiveOrNull(bundle.cpuFrequency()),
                positiveLong(bundle.freeMemory()),
                positiveLong(bundle.totalMemory()),
                positiveLong(bundle.freeHdd()),
                positiveLong(bundle.totalHdd()),
                now
        );

        EtherTrafficSnapshot etherSnapshot = buildEtherTrafficSnapshot(state, bundle.ether1Traffic(), now);
        List<PppoeClientSnapshot> pppoeSnapshots = buildPppoeSnapshots(state, bundle.pppSessions(), now);
        List<LogEntry> logs = mergeLogs(state, bundle.logs());
        updateWatchedUserHistory(state, pppoeSnapshots, now);

        return new RouterSnapshot(resourceSnapshot, etherSnapshot, pppoeSnapshots, logs, now);
    }

    private EtherTrafficSnapshot buildEtherTrafficSnapshot(RouterRuntimeState state,
                                                           MikrotikRouterOsApiClient.MikrotikLiveTrafficSnapshot traffic,
                                                           LocalDateTime collectedAt) {
        if (traffic == null) {
            return null;
        }

        state.etherHistory.addLast(new TrafficPoint(collectedAt, traffic.rxBps(), traffic.txBps()));
        trimDeque(state.etherHistory, ETHER_HISTORY_LIMIT);

        return new EtherTrafficSnapshot(
                traffic.interfaceName(),
                traffic.interfaceComment(),
                traffic.status(),
                positiveOrNull(traffic.speed()),
                traffic.rxBps(),
                traffic.txBps(),
                traffic.rxPackets(),
                traffic.txPackets(),
                traffic.rxErrors(),
                traffic.txErrors(),
                new ArrayList<>(state.etherHistory),
                collectedAt
        );
    }

    private List<PppoeClientSnapshot> buildPppoeSnapshots(RouterRuntimeState state,
                                                          List<MikrotikRouterOsApiClient.MikrotikPppSessionMonitoringSnapshot> sessions,
                                                          LocalDateTime collectedAt) {
        Map<String, CounterSnapshot> nextCounters = new LinkedHashMap<>();
        List<PppoeClientSnapshot> items = new ArrayList<>();
        for (MikrotikRouterOsApiClient.MikrotikPppSessionMonitoringSnapshot session : sessions) {
            String key = normalizeKey(session.username());
            CounterSnapshot previous = state.pppoeCounters.get(key);
            long rxRate = calculateRate(previous != null ? previous.rxBytes : null, session.rxBytes(), previous != null ? previous.capturedAt : null, collectedAt);
            long txRate = calculateRate(previous != null ? previous.txBytes : null, session.txBytes(), previous != null ? previous.capturedAt : null, collectedAt);
            nextCounters.put(key, new CounterSnapshot(session.rxBytes(), session.txBytes(), session.rxPackets(), session.txPackets(), collectedAt));
            items.add(new PppoeClientSnapshot(
                    session.username(),
                    session.interfaceName(),
                    session.ipAddress(),
                    session.callerId(),
                    session.profileName(),
                    session.service(),
                    session.status(),
                    session.uptimeSeconds(),
                    rxRate,
                    txRate,
                    session.rxPackets(),
                    session.txPackets(),
                    collectedAt
            ));
        }
        state.pppoeCounters = nextCounters;
        items.sort(Comparator.comparingLong((PppoeClientSnapshot item) -> item.rxRateBps() + item.txRateBps()).reversed()
                .thenComparing(PppoeClientSnapshot::username, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return items;
    }

    private List<LogEntry> mergeLogs(RouterRuntimeState state, List<MikrotikRouterOsApiClient.MikrotikLogSnapshot> logs) {
        Map<String, LogEntry> merged = new LinkedHashMap<>();
        for (LogEntry entry : state.logs) {
            merged.put(entry.fingerprint(), entry);
        }
        for (MikrotikRouterOsApiClient.MikrotikLogSnapshot item : logs) {
            String fingerprint = fingerprint(item.eventTime(), item.topics(), item.message());
            merged.put(fingerprint, new LogEntry(
                    item.eventTime(),
                    item.topics(),
                    item.message(),
                    inferSeverity(item.topics(), item.message()),
                    fingerprint
            ));
        }

        List<LogEntry> latest = merged.values().stream()
                .sorted(Comparator.comparing(LogEntry::time, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(LOG_LIMIT)
                .toList();
        state.logs = new ArrayList<>(latest);
        return state.logs;
    }

    private void updateWatchedUserHistory(RouterRuntimeState state, List<PppoeClientSnapshot> sessions, LocalDateTime collectedAt) {
        if (state.watchers.isEmpty()) {
            return;
        }
        Map<String, PppoeClientSnapshot> sessionsByName = new LinkedHashMap<>();
        for (PppoeClientSnapshot item : sessions) {
            sessionsByName.put(normalizeKey(item.username()), item);
        }

        for (Map.Entry<String, LocalDateTime> watcher : state.watchers.entrySet()) {
            if (watcher.getValue() == null || watcher.getValue().isBefore(collectedAt)) {
                continue;
            }
            UserTrafficHistory history = state.userTrafficHistory.computeIfAbsent(watcher.getKey(), ignored -> new UserTrafficHistory());
            PppoeClientSnapshot session = sessionsByName.get(watcher.getKey());
            if (session == null) {
                history.online = false;
                continue;
            }
            history.username = session.username();
            history.interfaceName = session.interfaceName();
            history.online = true;
            history.lastSeenAt = collectedAt;
            history.points.addLast(new TrafficPoint(collectedAt, session.rxRateBps(), session.txRateBps()));
            trimDeque(history.points, USER_HISTORY_LIMIT);
        }
    }

    private UserTrafficResponse buildUserTrafficResponse(MikrotikDevice router, RouterRuntimeState state, String username) {
        String key = normalizeKey(username);
        UserTrafficHistory history = state.userTrafficHistory.computeIfAbsent(key, ignored -> new UserTrafficHistory());
        PppoeClientSnapshot activeSession = state.snapshot != null && state.snapshot.pppoe != null
                ? state.snapshot.pppoe.stream()
                .filter(item -> normalizeKey(item.username()).equals(key))
                .findFirst()
                .orElse(null)
                : null;
        if (activeSession != null && history.points.isEmpty()) {
            history.username = activeSession.username();
            history.interfaceName = activeSession.interfaceName();
            history.online = true;
            history.lastSeenAt = activeSession.collectedAt();
            history.points.add(new TrafficPoint(activeSession.collectedAt(), activeSession.rxRateBps(), activeSession.txRateBps()));
        }

        return new UserTrafficResponse(
                history.username != null ? history.username : username,
                router.getId(),
                router.resolveDeviceName(),
                history.interfaceName,
                history.online,
                history.lastSeenAt,
                new ArrayList<>(history.points),
                buildStateInfo(state)
        );
    }

    private RouterSummary buildRouterSummary(MikrotikDevice router, RouterRuntimeState state) {
        RouterSnapshot snapshot = state.snapshot;
        return new RouterSummary(
                router.getId(),
                router.resolveDeviceName(),
                firstText(router.resolveVpnHost(), router.getIpAddress()),
                router.resolveCurrentStatus(),
                snapshot != null ? snapshot.resources : null,
                snapshot != null ? snapshot.ether1 : null,
                snapshot != null ? snapshot.pppoe : List.of(),
                snapshot != null ? snapshot.logs : List.of(),
                buildStateInfo(state)
        );
    }

    private RouterSelection resolveSelection(Long requestedRouterId) {
        List<MikrotikDevice> routers = routerRepository.findActiveRoutersOrdered();
        if (routers.isEmpty()) {
            RouterSelectionMeta meta = new RouterSelectionMeta(0, false, null, null, null, true);
            return new RouterSelection(routers, null, meta);
        }

        MikrotikDevice defaultRouter = routers.getFirst();
        MikrotikDevice selected = defaultRouter;
        if (requestedRouterId != null) {
            selected = routers.stream()
                    .filter(item -> Objects.equals(item.getId(), requestedRouterId))
                    .findFirst()
                    .orElse(defaultRouter);
        }
        RouterSelectionMeta meta = new RouterSelectionMeta(
                routers.size(),
                routers.size() > 1,
                defaultRouter.getId(),
                selected.getId(),
                selected.resolveDeviceName(),
                routers.size() == 1
        );
        return new RouterSelection(routers, selected, meta);
    }

    private void touchRouter(Long routerId) {
        stateByRouterId.computeIfAbsent(routerId, ignored -> new RouterRuntimeState()).leaseUntil = LocalDateTime.now().plus(ROUTER_LEASE);
    }

    private RouterOption toRouterOption(MikrotikDevice router) {
        RouterRuntimeState state = stateByRouterId.get(router.getId());
        return new RouterOption(
                router.getId(),
                router.resolveDeviceName(),
                firstText(router.resolveVpnHost(), router.getIpAddress()),
                router.resolveCurrentStatus(),
                router.isActive(),
                router.getLastSeenAt(),
                state != null ? state.lastSuccessAt : null,
                state != null ? state.lastError : null
        );
    }

    private MikrotikConnectionService.ConnectionTarget resolveTarget(MikrotikDevice router) {
        if (router.resolveApiUsername() == null || router.resolveApiPassword() == null) {
            throw new IllegalStateException("Credential API router belum lengkap");
        }
        List<MikrotikConnectionService.ResolvedTarget> candidates = mikrotikConnectionService.resolveApiCandidates(
                router.getMonitoringTarget(),
                router.getApiIpAddress(),
                router.getWinboxIpAddress(),
                router.getVpnIpAddress(),
                router.getIpAddress()
        );
        if (candidates.isEmpty()) {
            throw new IllegalStateException("Target koneksi API router belum valid");
        }
        return candidates.getFirst().target();
    }

    private StateInfo buildStateInfo(RouterRuntimeState state) {
        LocalDateTime now = LocalDateTime.now();
        boolean fresh = state.lastSuccessAt != null && state.lastSuccessAt.plus(SNAPSHOT_TTL).isAfter(now);
        boolean offline = state.circuitOpenUntil != null && state.circuitOpenUntil.isAfter(now);
        String status = offline ? "offline" : (fresh ? "fresh" : "stale");
        return new StateInfo(status, fresh, offline, state.lastSuccessAt, state.lastError, state.circuitOpenUntil, state.lastDurationMs);
    }

    private PollingProfile pollingProfile() {
        return new PollingProfile(8000, 8000, 8000, 8000, 8000, ETHER_HISTORY_LIMIT, USER_HISTORY_LIMIT);
    }

    private void pruneWatchers(RouterRuntimeState state, LocalDateTime now) {
        state.watchers.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isBefore(now));
    }

    private long calculateRate(Long previousValue, long currentValue, LocalDateTime previousAt, LocalDateTime currentAt) {
        if (previousValue == null || previousAt == null || currentAt == null || currentAt.isEqual(previousAt) || currentValue < previousValue) {
            return 0L;
        }
        long seconds = Math.max(1, Duration.between(previousAt, currentAt).getSeconds());
        return ((currentValue - previousValue) * 8L) / seconds;
    }

    private String inferSeverity(String topics, String message) {
        String haystack = ((topics != null ? topics : "") + " " + (message != null ? message : ""))
                .toLowerCase(Locale.ROOT);
        if (haystack.contains("critical") || haystack.contains("error") || haystack.contains("failed")) {
            return "error";
        }
        if (haystack.contains("warn") || haystack.contains("down") || haystack.contains("timeout")) {
            return "warning";
        }
        return "info";
    }

    private String fingerprint(LocalDateTime time, String topics, String message) {
        return String.join("|",
                time != null ? time.toString() : "",
                topics != null ? topics.trim().toLowerCase(Locale.ROOT) : "",
                message != null ? message.trim().toLowerCase(Locale.ROOT) : "");
    }

    private Integer positiveOrNull(long value) {
        if (value <= 0L || value > Integer.MAX_VALUE) {
            return null;
        }
        return (int) value;
    }

    private Long positiveLong(long value) {
        return value > 0L ? value : null;
    }

    private String valueText(String first, String fallback) {
        String value = firstText(first, fallback);
        return value != null ? value : "-";
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private <T> void trimDeque(Deque<T> deque, int limit) {
        while (deque.size() > limit) {
            deque.removeFirst();
        }
    }

    private record RouterSelection(List<MikrotikDevice> routers, MikrotikDevice selectedRouter, RouterSelectionMeta meta) {
    }

    private static final class RouterRuntimeState {
        private volatile LocalDateTime leaseUntil;
        private volatile LocalDateTime lastSuccessAt;
        private volatile LocalDateTime lastFailureAt;
        private volatile LocalDateTime circuitOpenUntil;
        private volatile String lastError;
        private volatile int failureCount;
        private volatile long lastDurationMs;
        private volatile RouterSnapshot snapshot;
        private volatile Map<String, CounterSnapshot> pppoeCounters = new LinkedHashMap<>();
        private final Deque<TrafficPoint> etherHistory = new ArrayDeque<>();
        private volatile List<LogEntry> logs = new ArrayList<>();
        private final ConcurrentMap<String, LocalDateTime> watchers = new ConcurrentHashMap<>();
        private final ConcurrentMap<String, UserTrafficHistory> userTrafficHistory = new ConcurrentHashMap<>();
    }

    private record CounterSnapshot(long rxBytes, long txBytes, long rxPackets, long txPackets, LocalDateTime capturedAt) {
    }

    private static final class UserTrafficHistory {
        private String username;
        private String interfaceName;
        private boolean online;
        private LocalDateTime lastSeenAt;
        private final Deque<TrafficPoint> points = new ArrayDeque<>();
    }

    private record RouterSnapshot(
            ResourceSnapshot resources,
            EtherTrafficSnapshot ether1,
            List<PppoeClientSnapshot> pppoe,
            List<LogEntry> logs,
            LocalDateTime collectedAt
    ) {
    }

    public record RouterListResponse(
            List<RouterOption> items,
            int total,
            boolean showFilter,
            Long defaultRouterId,
            Long singleRouterId
    ) {
    }

    public record RouterOption(
            Long id,
            String name,
            String host,
            String status,
            boolean active,
            LocalDateTime lastSeenAt,
            LocalDateTime lastSnapshotAt,
            String lastError
    ) {
    }

    public record RouterSelectionMeta(
            int routerCount,
            boolean showFilter,
            Long defaultRouterId,
            Long selectedRouterId,
            String selectedRouterName,
            boolean singleRouter
    ) {
    }

    public record MonitoringSummaryResponse(
            RouterSelectionMeta selection,
            RouterSummary router,
            PollingProfile polling
    ) {
        public static MonitoringSummaryResponse empty(RouterSelectionMeta selection, PollingProfile polling) {
            return new MonitoringSummaryResponse(selection, null, polling);
        }
    }

    public record RouterSummary(
            Long id,
            String name,
            String host,
            String status,
            ResourceSnapshot resources,
            EtherTrafficSnapshot ether1,
            List<PppoeClientSnapshot> pppoe,
            List<LogEntry> logs,
            StateInfo state
    ) {
    }

    public record ResourceSnapshot(
            String identityName,
            String version,
            String boardName,
            String architecture,
            long uptimeSeconds,
            Integer cpuLoad,
            Integer cpuCount,
            Integer cpuFrequencyMhz,
            Long freeMemoryBytes,
            Long totalMemoryBytes,
            Long freeHddBytes,
            Long totalHddBytes,
            LocalDateTime collectedAt
    ) {
    }

    public record EtherTrafficSnapshot(
            String interfaceName,
            String comment,
            String status,
            Integer speedBps,
            long rxRateBps,
            long txRateBps,
            long rxPackets,
            long txPackets,
            long rxErrors,
            long txErrors,
            List<TrafficPoint> history,
            LocalDateTime collectedAt
    ) {
    }

    public record PppoeClientSnapshot(
            String username,
            String interfaceName,
            String ipAddress,
            String callerId,
            String profile,
            String service,
            String status,
            long uptimeSeconds,
            long rxRateBps,
            long txRateBps,
            long rxPackets,
            long txPackets,
            LocalDateTime collectedAt
    ) {
    }

    public record LogEntry(
            LocalDateTime time,
            String topics,
            String message,
            String severity,
            String fingerprint
    ) {
    }

    public record TrafficPoint(
            LocalDateTime time,
            long rxRateBps,
            long txRateBps
    ) {
    }

    public record TrafficResponse(
            EtherTrafficSnapshot current,
            List<TrafficPoint> history,
            StateInfo state
    ) {
    }

    public record ResourceResponse(
            ResourceSnapshot resources,
            StateInfo state
    ) {
    }

    public record PppoeListResponse(
            List<PppoeClientSnapshot> items,
            int total,
            StateInfo state
    ) {
    }

    public record LogResponse(
            List<LogEntry> items,
            StateInfo state
    ) {
    }

    public record UserTrafficResponse(
            String username,
            Long routerId,
            String routerName,
            String interfaceName,
            boolean online,
            LocalDateTime lastSeenAt,
            List<TrafficPoint> history,
            StateInfo state
    ) {
        public static UserTrafficResponse empty(String username, StateInfo state) {
            return new UserTrafficResponse(username, null, null, null, false, null, List.of(), state);
        }
    }

    public record StateInfo(
            String status,
            boolean fresh,
            boolean offline,
            LocalDateTime lastSuccessAt,
            String lastError,
            LocalDateTime retryAfter,
            long lastDurationMs
    ) {
        public static StateInfo empty(String message) {
            return new StateInfo("empty", false, false, null, message, null, 0L);
        }
    }

    public record PollingProfile(
            long summaryIntervalMs,
            long ether1IntervalMs,
            long resourceIntervalMs,
            long pppoeIntervalMs,
            long logsIntervalMs,
            int etherHistoryLimit,
            int pppoeHistoryLimit
    ) {
    }
}
