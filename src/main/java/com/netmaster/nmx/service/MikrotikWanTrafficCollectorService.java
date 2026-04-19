package com.netmaster.nmx.service;

import com.netmaster.nmx.model.DeviceMetrics;
import com.netmaster.nmx.model.MikrotikDevice;
import com.netmaster.nmx.model.NetworkDevice;
import com.netmaster.nmx.model.Server;
import com.netmaster.nmx.repository.DeviceMetricsRepository;
import com.netmaster.nmx.repository.MikrotikDeviceRepository;
import com.netmaster.nmx.repository.NetworkDeviceRepository;
import com.netmaster.nmx.repository.ServerRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class MikrotikWanTrafficCollectorService {

    private static final int POLL_INTERVAL_MS = 2_000;
    private static final int INITIAL_DELAY_MS = 15_000;
    private static final int MAX_BUFFER_SIZE = 90;
    private static final int MAX_ATTEMPT_HISTORY = 12;
    private static final Duration STALE_AFTER = Duration.ofSeconds(8);
    private static final Duration DISCONNECTED_AFTER = Duration.ofSeconds(20);
    private static final DateTimeFormatter LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ServerRepository serverRepository;
    private final MikrotikDeviceRepository mikrotikRepository;
    private final NetworkDeviceRepository networkDeviceRepository;
    private final DeviceMetricsRepository deviceMetricsRepository;
    private final MikrotikConnectionService mikrotikConnectionService;
    private final MikrotikRouterOsApiClient mikrotikRouterOsApiClient;

    private final ConcurrentMap<Long, CollectorState> states = new ConcurrentHashMap<>();
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();
    private final ExecutorService collectorExecutor = Executors.newFixedThreadPool(
            Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())),
            new CustomizableThreadFactory("mikrotik-wan-collector-")
    );

    @Scheduled(fixedDelay = POLL_INTERVAL_MS, initialDelay = INITIAL_DELAY_MS)
    public void scheduleCollection() {
        List<Server> activeServers = serverRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .filter(server -> server.getMikrotikId() != null)
                .toList();

        Set<Long> activeServerIds = new HashSet<>();
        for (Server server : activeServers) {
            activeServerIds.add(server.getId());
            submitCollection(server.getId(), false);
        }

        states.keySet().removeIf(serverId -> !activeServerIds.contains(serverId));
    }

    public Map<String, Object> getRealtimePayload(Long serverId) {
        ensureSampleAvailability(serverId);
        CollectorState state = stateFor(serverId);
        synchronized (state) {
            return buildRealtimePayload(state, determineStatus(state, LocalDateTime.now()));
        }
    }

    public Map<String, Object> getStatusPayload(Long serverId) {
        ensureSampleAvailability(serverId);
        CollectorState state = stateFor(serverId);
        synchronized (state) {
            return buildStatusPayload(state, determineStatus(state, LocalDateTime.now()));
        }
    }

    public List<Map<String, Object>> getStatusPayloads() {
        List<Server> activeServers = serverRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .filter(server -> server.getMikrotikId() != null)
                .toList();
        return activeServers.stream()
                .map(server -> {
                    CollectorState state = stateFor(server.getId());
                    synchronized (state) {
                        state.serverId = server.getId();
                        state.serverName = safeText(server.getName());
                        state.serverLocation = safeText(server.getLocation());
                    }
                    if (shouldCollectOnDemand(state)) {
                        submitCollection(server.getId(), true);
                    }
                    synchronized (state) {
                        return buildStatusPayload(state, determineStatus(state, LocalDateTime.now()));
                    }
                })
                .toList();
    }

    public Map<String, Object> getInterfaceRow(Long serverId) {
        ensureSampleAvailability(serverId);
        CollectorState state = stateFor(serverId);
        synchronized (state) {
            CollectorStatus status = determineStatus(state, LocalDateTime.now());
            return buildInterfaceRow(state, status);
        }
    }

    @PreDestroy
    void shutdownExecutor() {
        collectorExecutor.shutdownNow();
        try {
            collectorExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void ensureSampleAvailability(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .filter(item -> !Boolean.FALSE.equals(item.getIsActive()))
                .orElseThrow(() -> new NoSuchElementException("Server tidak ditemukan"));

        if (server.getMikrotikId() == null) {
            throw new IllegalArgumentException("Server belum terhubung ke Mikrotik");
        }

        CollectorState state = states.computeIfAbsent(serverId, ignored -> new CollectorState());
        synchronized (state) {
            if (state.samples.isEmpty() && state.lastAttemptAt == null) {
                state.serverId = server.getId();
                state.serverName = safeText(server.getName());
                state.serverLocation = safeText(server.getLocation());
            }
        }

        if (shouldCollectOnDemand(state)) {
            submitCollection(serverId, true);
        }
    }

    private boolean shouldCollectOnDemand(CollectorState state) {
        synchronized (state) {
            if (!state.samples.isEmpty()) {
                return false;
            }
            if (state.lastAttemptAt == null) {
                return true;
            }
            return Duration.between(state.lastAttemptAt, LocalDateTime.now()).compareTo(Duration.ofSeconds(3)) > 0;
        }
    }

    private CollectorState stateFor(Long serverId) {
        return states.computeIfAbsent(serverId, ignored -> new CollectorState());
    }

    private void submitCollection(Long serverId, boolean force) {
        if (!inFlight.add(serverId)) {
            return;
        }
        collectorExecutor.submit(() -> {
            try {
                collectServerTraffic(serverId);
            } finally {
                inFlight.remove(serverId);
            }
        });
    }

    private void collectServerTraffic(Long serverId) {
        CollectorState state = stateFor(serverId);
        LocalDateTime now = LocalDateTime.now();
        synchronized (state) {
            state.lastAttemptAt = now;
            state.serverId = serverId;
        }

        ResolvedServerContext context;
        try {
            context = resolveContext(serverId);
        } catch (RuntimeException ex) {
            synchronized (state) {
                state.configError = ex.getMessage();
                state.lastError = ex.getMessage();
                state.consecutiveFailures++;
            }
            logFailure(serverId, safeText(ex.getMessage()), state.consecutiveFailures);
            return;
        }

        synchronized (state) {
            state.serverName = safeText(context.server().getName());
            state.serverLocation = safeText(context.server().getLocation());
            state.mikrotikId = context.mikrotik().getId();
            state.mikrotikName = safeText(context.mikrotik().getName());
            state.networkDeviceId = context.networkDevice() != null ? context.networkDevice().getId() : null;
            state.configError = null;
        }

        bootstrapFromDatabaseIfNeeded(state, context.networkDevice());

        String preferredInterface = resolvePreferredWanInterfaceName(state, context.networkDevice());
        List<MikrotikConnectionService.ResolvedTarget> candidates = mikrotikConnectionService.resolveApiCandidates(
                context.mikrotik().getMonitoringTarget(),
                context.mikrotik().getApiIpAddress(),
                context.mikrotik().getWinboxIpAddress(),
                context.mikrotik().resolveVpnEndpoint(),
                context.mikrotik().getIpAddress()
        );
        if (candidates.isEmpty()) {
            synchronized (state) {
                state.lastError = "Alamat API Mikrotik untuk server ini belum valid";
                state.consecutiveFailures++;
                state.preferredInterfaceHint = preferredInterface;
                state.candidateTargets = List.of();
                state.addAttempt(AttemptDiagnostic.configFailure(
                        "config",
                        null,
                        null,
                        0L,
                        state.lastError,
                        now
                ));
            }
            logFailure(serverId, state.lastError, state.consecutiveFailures);
            return;
        }

        List<AttemptDiagnostic> attempts = new ArrayList<>();
        List<Map<String, Object>> candidateTargets = candidates.stream()
                .map(candidate -> candidateView(candidate.source(), candidate.target().host(), candidate.target().port()))
                .toList();

        IllegalStateException lastFailure = null;
        for (MikrotikConnectionService.ResolvedTarget candidate : candidates) {
            long startedAt = System.nanoTime();
            try {
                MikrotikRouterOsApiClient.MikrotikLiveTrafficSnapshot snapshot = mikrotikRouterOsApiClient.collectWanTraffic(
                        candidate.target(),
                        context.mikrotik().getUsername(),
                        context.mikrotik().getPassword(),
                        preferredInterface
                );
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                attempts.add(AttemptDiagnostic.success(
                        candidate.source(),
                        candidate.target().host(),
                        candidate.target().port(),
                        durationMs,
                        now,
                        snapshot.interfaceName()
                ));
                int previousFailures;
                synchronized (state) {
                    previousFailures = state.consecutiveFailures;
                    state.addSample(snapshot);
                    state.lastSuccessAt = snapshot.collectedAt();
                    state.lastError = null;
                    state.lastFailedAttempt = null;
                    state.consecutiveFailures = 0;
                    state.targetSource = candidate.source();
                    state.targetHost = candidate.target().host();
                    state.targetPort = candidate.target().port();
                    state.transport = "routeros-api";
                    state.preferredInterfaceHint = preferredInterface;
                    state.candidateTargets = candidateTargets;
                    state.recordAttempts(attempts);
                }
                logRecovery(serverId, state, durationMs, previousFailures);
                return;
            } catch (IllegalStateException ex) {
                lastFailure = ex;
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
                attempts.add(AttemptDiagnostic.failure(
                        candidate.source(),
                        candidate.target().host(),
                        candidate.target().port(),
                        durationMs,
                        rootCauseMessage(ex),
                        rootCauseType(ex),
                        now
                ));
            }
        }

        String errorMessage = lastFailure != null ? safeText(lastFailure.getMessage()) : "Gagal mengambil trafik realtime MikroTik";
        synchronized (state) {
            state.lastError = errorMessage;
            state.consecutiveFailures++;
            state.preferredInterfaceHint = preferredInterface;
            state.candidateTargets = candidateTargets;
            state.recordAttempts(attempts);
        }
        logFailure(serverId, errorMessage, state.consecutiveFailures, attempts);
    }

    private void bootstrapFromDatabaseIfNeeded(CollectorState state, NetworkDevice networkDevice) {
        synchronized (state) {
            if (!state.samples.isEmpty() || state.bootstrapLoaded) {
                return;
            }
        }

        findLatestWanMetric(networkDevice).ifPresent(metric -> {
            synchronized (state) {
                if (!state.samples.isEmpty()) {
                    return;
                }
                state.bootstrapLoaded = true;
                state.interfaceName = safeText(metric.getInterfaceName());
                state.interfaceStatus = safeText(metric.getInterfaceStatus());
                state.interfaceSpeed = metric.getInterfaceSpeed();
                state.addSample(new TrafficSample(
                        metric.getTimestamp() != null ? metric.getTimestamp() : LocalDateTime.now(),
                        defaultLong(metric.getTrafficRxBps()),
                        defaultLong(metric.getTrafficTxBps()),
                        defaultLong(metric.getPacketsRx()),
                        defaultLong(metric.getPacketsTx()),
                        defaultLong(metric.getErrorsRx()),
                        defaultLong(metric.getErrorsTx())
                ));
                state.transport = "database";
            }
        });
    }

    private ResolvedServerContext resolveContext(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .filter(item -> !Boolean.FALSE.equals(item.getIsActive()))
                .orElseThrow(() -> new NoSuchElementException("Server tidak ditemukan"));
        if (server.getMikrotikId() == null) {
            throw new IllegalArgumentException("Server belum terhubung ke Mikrotik");
        }

        MikrotikDevice mikrotik = mikrotikRepository.findById(server.getMikrotikId())
                .orElseThrow(() -> new NoSuchElementException("Mikrotik untuk server ini tidak ditemukan"));
        if (!hasText(mikrotik.getUsername()) || !hasText(mikrotik.getPassword())) {
            throw new IllegalArgumentException("Username atau password API Mikrotik belum diisi");
        }

        return new ResolvedServerContext(server, mikrotik, resolveNetworkDevice(mikrotik));
    }

    private NetworkDevice resolveNetworkDevice(MikrotikDevice mikrotik) {
        String mikrotikHost = mikrotikConnectionService.extractHost(firstText(
                mikrotik.getApiIpAddress(),
                mikrotik.resolveVpnHost(),
                mikrotik.getIpAddress(),
                mikrotik.getWinboxIpAddress()
        ));

        return networkDeviceRepository.findByIsActiveTrue().stream()
                .filter(device -> device.getDeviceType() == NetworkDevice.DeviceType.MIKROTIK)
                .filter(device -> Objects.equals(device.getSourceId(), mikrotik.getId())
                        || Objects.equals(normalize(device.getIpAddress()), normalize(mikrotikHost))
                        || Objects.equals(normalize(device.getDeviceName()), normalize(mikrotik.getName())))
                .max(Comparator.comparing(NetworkDevice::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
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

    private String resolvePreferredWanInterfaceName(CollectorState state, NetworkDevice device) {
        synchronized (state) {
            if (hasText(state.interfaceName)) {
                return state.interfaceName;
            }
        }
        return findLatestWanMetric(device)
                .map(DeviceMetrics::getInterfaceName)
                .filter(this::hasText)
                .orElse(null);
    }

    private CollectorStatus determineStatus(CollectorState state, LocalDateTime now) {
        if (hasText(state.configError)) {
            return CollectorStatus.INVALID_CONFIG;
        }
        if (state.lastSuccessAt == null) {
            if (hasText(state.lastError)) {
                return CollectorStatus.DISCONNECTED;
            }
            return CollectorStatus.WAITING;
        }

        boolean latestAttemptFailed = state.lastAttemptAt != null
                && state.lastAttemptAt.isAfter(state.lastSuccessAt)
                && hasText(state.lastError);
        Duration age = Duration.between(state.lastSuccessAt, now);
        if (age.compareTo(DISCONNECTED_AFTER) > 0) {
            return CollectorStatus.DISCONNECTED;
        }
        if (latestAttemptFailed || age.compareTo(STALE_AFTER) > 0) {
            return CollectorStatus.STALE;
        }
        return CollectorStatus.CONNECTED;
    }

    private Map<String, Object> buildRealtimePayload(CollectorState state, CollectorStatus collectorStatus) {
        List<TrafficSample> samples = new ArrayList<>(state.samples);
        TrafficSample latest = samples.isEmpty() ? null : samples.getLast();
        List<Double> downloadSeries = samples.stream().map(sample -> toMbps(sample.downloadBps())).toList();
        List<Double> uploadSeries = samples.stream().map(sample -> toMbps(sample.uploadBps())).toList();
        List<String> labels = samples.stream()
                .map(sample -> sample.timestamp() != null ? sample.timestamp().format(LABEL_FORMATTER) : "--:--:--")
                .toList();

        Map<String, Object> stats = calculateStats(samples);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serverId", state.serverId);
        payload.put("serverName", state.serverName);
        payload.put("serverLocation", state.serverLocation);
        payload.put("mikrotikId", state.mikrotikId);
        payload.put("mikrotikName", state.mikrotikName);
        payload.put("interfaceName", safeText(state.interfaceName));
        payload.put("interfaceComment", safeText(state.interfaceComment));
        payload.put("interfaceStatus", safeText(state.interfaceStatus));
        payload.put("status", safeText(state.interfaceStatus));
        payload.put("connectionStatus", collectorStatus.name().toLowerCase(Locale.ROOT));
        payload.put("collectorMessage", buildCollectorMessage(state, collectorStatus));
        payload.put("errorMessage", safeText(state.lastError));
        payload.put("preferredInterfaceHint", safeText(state.preferredInterfaceHint));
        payload.put("networkDeviceId", state.networkDeviceId);
        payload.put("stale", collectorStatus == CollectorStatus.STALE);
        payload.put("disconnected", collectorStatus == CollectorStatus.DISCONNECTED);
        payload.put("fallback", collectorStatus != CollectorStatus.CONNECTED);
        payload.put("targetSource", safeText(state.targetSource));
        payload.put("targetHost", safeText(state.targetHost));
        payload.put("targetPort", state.targetPort);
        payload.put("transport", safeText(state.transport));
        payload.put("candidateTargets", state.candidateTargets);
        payload.put("recentAttempts", state.recentAttempts.stream().map(AttemptDiagnostic::toMap).toList());
        payload.put("lastAttemptSummary", state.recentAttempts.peekLast() != null ? state.recentAttempts.peekLast().summary() : null);
        payload.put("speed", state.interfaceSpeed != null && state.interfaceSpeed > 0 ? formatBandwidth(state.interfaceSpeed) : null);
        payload.put("speedBps", state.interfaceSpeed);
        payload.put("timestamp", latest != null ? latest.timestamp() : null);
        payload.put("lastSuccessAt", state.lastSuccessAt);
        payload.put("lastAttemptAt", state.lastAttemptAt);
        payload.put("lastFailureAt", state.lastFailureAt);
        payload.put("sampleAgeSeconds", latest != null && latest.timestamp() != null
                ? Math.max(0L, Duration.between(latest.timestamp(), LocalDateTime.now()).getSeconds())
                : null);
        payload.put("sampleCount", samples.size());
        payload.put("download", latest != null ? toMbps(latest.downloadBps()) : null);
        payload.put("upload", latest != null ? toMbps(latest.uploadBps()) : null);
        payload.put("currentDownload", latest != null ? toMbps(latest.downloadBps()) : null);
        payload.put("currentUpload", latest != null ? toMbps(latest.uploadBps()) : null);
        payload.put("downloadBps", latest != null ? latest.downloadBps() : null);
        payload.put("uploadBps", latest != null ? latest.uploadBps() : null);
        payload.put("rxPackets", latest != null ? latest.rxPackets() : null);
        payload.put("txPackets", latest != null ? latest.txPackets() : null);
        payload.put("rxErrors", latest != null ? latest.rxErrors() : null);
        payload.put("txErrors", latest != null ? latest.txErrors() : null);
        payload.putAll(stats);
        payload.put("labels", labels);
        payload.put("seriesDownload", downloadSeries);
        payload.put("seriesUpload", uploadSeries);
        payload.put("series", Map.of(
                "labels", labels,
                "download", downloadSeries,
                "upload", uploadSeries
        ));
        payload.put("realtime", true);
        return payload;
    }

    private Map<String, Object> buildStatusPayload(CollectorState state, CollectorStatus collectorStatus) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serverId", state.serverId);
        payload.put("serverName", state.serverName);
        payload.put("serverLocation", state.serverLocation);
        payload.put("mikrotikId", state.mikrotikId);
        payload.put("mikrotikName", state.mikrotikName);
        payload.put("interfaceName", safeText(state.interfaceName));
        payload.put("connectionStatus", collectorStatus.name().toLowerCase(Locale.ROOT));
        payload.put("collectorMessage", buildCollectorMessage(state, collectorStatus));
        payload.put("errorMessage", safeText(state.lastError));
        payload.put("preferredInterfaceHint", safeText(state.preferredInterfaceHint));
        payload.put("networkDeviceId", state.networkDeviceId);
        payload.put("stale", collectorStatus == CollectorStatus.STALE);
        payload.put("disconnected", collectorStatus == CollectorStatus.DISCONNECTED);
        payload.put("targetSource", safeText(state.targetSource));
        payload.put("targetHost", safeText(state.targetHost));
        payload.put("targetPort", state.targetPort);
        payload.put("transport", safeText(state.transport));
        payload.put("candidateTargets", state.candidateTargets);
        payload.put("recentAttempts", state.recentAttempts.stream().map(AttemptDiagnostic::toMap).toList());
        payload.put("lastAttemptSummary", state.recentAttempts.peekLast() != null ? state.recentAttempts.peekLast().summary() : null);
        payload.put("sampleCount", state.samples.size());
        payload.put("lastSuccessAt", state.lastSuccessAt);
        payload.put("lastAttemptAt", state.lastAttemptAt);
        payload.put("lastFailureAt", state.lastFailureAt);
        return payload;
    }

    private Map<String, Object> buildInterfaceRow(CollectorState state, CollectorStatus collectorStatus) {
        TrafficSample latest = state.samples.peekLast();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", state.serverId);
        row.put("interface", safeText(state.interfaceName));
        row.put("deviceName", safeText(state.mikrotikName));
        row.put("deviceLocation", safeText(state.serverLocation));
        row.put("download", latest != null ? toMbps(latest.downloadBps()) : null);
        row.put("upload", latest != null ? toMbps(latest.uploadBps()) : null);
        row.put("utilization", latest != null && state.interfaceSpeed != null && state.interfaceSpeed > 0
                ? BigDecimal.valueOf((latest.downloadBps() + latest.uploadBps()) * 100.0d / state.interfaceSpeed)
                .setScale(0, RoundingMode.HALF_UP)
                .intValue()
                : null);
        row.put("packetErrors", latest != null ? latest.rxErrors() + latest.txErrors() : null);
        row.put("status", safeText(state.interfaceStatus));
        row.put("interfaceComment", safeText(state.interfaceComment));
        row.put("connectionStatus", collectorStatus.name().toLowerCase(Locale.ROOT));
        row.put("fallback", collectorStatus != CollectorStatus.CONNECTED);
        row.put("fallbackReason", safeText(state.lastError));
        row.put("lastAttemptSummary", state.recentAttempts.peekLast() != null ? state.recentAttempts.peekLast().summary() : null);
        row.put("realtime", collectorStatus == CollectorStatus.CONNECTED);
        return row;
    }

    private Map<String, Object> calculateStats(List<TrafficSample> samples) {
        List<Double> downloads = samples.stream().map(sample -> toMbps(sample.downloadBps())).filter(Objects::nonNull).toList();
        List<Double> uploads = samples.stream().map(sample -> toMbps(sample.uploadBps())).filter(Objects::nonNull).toList();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("peakDownload", max(downloads));
        stats.put("peakUpload", max(uploads));
        stats.put("avgDownload", average(downloads));
        stats.put("avgUpload", average(uploads));
        return stats;
    }

    private Double max(Collection<Double> values) {
        return values.stream().filter(Objects::nonNull).max(Double::compareTo).map(this::roundDouble).orElse(null);
    }

    private Double average(Collection<Double> values) {
        List<Double> validValues = values.stream().filter(Objects::nonNull).toList();
        if (validValues.isEmpty()) {
            return null;
        }
        return roundDouble(validValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d));
    }

    private Double roundDouble(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private String buildCollectorMessage(CollectorState state, CollectorStatus collectorStatus) {
        String interfaceLabel = hasText(state.interfaceName) ? state.interfaceName : "WAN";
        return switch (collectorStatus) {
            case CONNECTED -> String.format(
                    Locale.ROOT,
                    "Live %s %s:%s | %s | %s",
                    safeText(firstText(state.targetSource, state.transport)),
                    safeText(firstText(state.targetHost, "-")),
                    state.targetPort != null ? state.targetPort : "-",
                    interfaceLabel,
                    safeText(firstText(state.recentAttempts.peekLast() != null ? state.recentAttempts.peekLast().summary() : null, "attempt ok"))
            );
            case STALE -> "Menggunakan sample terakhir. "
                    + safeText(firstText(resolveFailureSummary(state), resolveStaleSummary(state), state.lastError, "tidak ada detail error"));
            case DISCONNECTED -> "Koneksi MikroTik terputus. Sample lama tetap ditampilkan: "
                    + safeText(firstText(resolveFailureSummary(state), resolveStaleSummary(state), state.lastError, "tidak ada detail error"));
            case INVALID_CONFIG -> "Konfigurasi monitoring belum valid: " + safeText(firstText(state.configError, state.lastError));
            case WAITING -> "Collector sedang menunggu sample pertama dari MikroTik.";
        };
    }

    private String resolveFailureSummary(CollectorState state) {
        if (state.lastFailedAttempt != null) {
            return state.lastFailedAttempt.summary();
        }
        return null;
    }

    private String resolveStaleSummary(CollectorState state) {
        if (state.lastSuccessAt == null) {
            return null;
        }
        long ageSeconds = Math.max(0L, Duration.between(state.lastSuccessAt, LocalDateTime.now()).getSeconds());
        return "sample terakhir berumur " + ageSeconds + " detik";
    }

    private void logFailure(Long serverId, String message, int consecutiveFailures) {
        logFailure(serverId, message, consecutiveFailures, List.of());
    }

    private void logFailure(Long serverId, String message, int consecutiveFailures, List<AttemptDiagnostic> attempts) {
        String attemptSummary = attempts.isEmpty()
                ? ""
                : " | attempts=" + attempts.stream().map(AttemptDiagnostic::summary).toList();
        if (consecutiveFailures == 1 || consecutiveFailures == 5 || consecutiveFailures % 15 == 0) {
            log.warn("WAN collector failed for server {} after {} attempts: {}{}", serverId, consecutiveFailures, message, attemptSummary);
        } else {
            log.debug("WAN collector failed for server {} after {} attempts: {}{}", serverId, consecutiveFailures, message, attemptSummary);
        }
    }

    private void logRecovery(Long serverId, CollectorState state, long durationMs, int previousFailures) {
        if (previousFailures > 0) {
            log.info("WAN collector recovered for server {} via {} {}:{} in {} ms", serverId,
                    safeText(state.targetSource), safeText(state.targetHost), state.targetPort, durationMs);
        } else {
            log.debug("WAN collector success for server {} via {} {}:{} in {} ms", serverId,
                    safeText(state.targetSource), safeText(state.targetHost), state.targetPort, durationMs);
        }
    }

    private Map<String, Object> candidateView(String source, String host, Integer port) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("source", source);
        item.put("host", host);
        item.put("port", port);
        return item;
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return safeText(firstText(current.getMessage(), throwable.getMessage(), "tidak ada detail error"));
    }

    private String rootCauseType(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    private boolean isWanInterface(String interfaceName) {
        String normalized = normalize(interfaceName);
        return normalized != null && normalized.toLowerCase(Locale.ROOT).contains("wan");
    }

    private boolean isEthernetInterface(String interfaceName) {
        String normalized = normalize(interfaceName);
        return normalized != null && normalized.toLowerCase(Locale.ROOT).startsWith("ether");
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return normalize(value) != null;
    }

    private String safeText(String value) {
        return normalize(value);
    }

    private String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private long defaultLong(Long value) {
        return value != null ? value : 0L;
    }

    private Double toMbps(long bps) {
        return BigDecimal.valueOf(bps)
                .divide(BigDecimal.valueOf(1_000_000L), 2, RoundingMode.HALF_UP)
                .doubleValue();
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

    private enum CollectorStatus {
        WAITING,
        CONNECTED,
        STALE,
        DISCONNECTED,
        INVALID_CONFIG
    }

    private record TrafficSample(
            LocalDateTime timestamp,
            long downloadBps,
            long uploadBps,
            long rxPackets,
            long txPackets,
            long rxErrors,
            long txErrors
    ) {
    }

    private record ResolvedServerContext(Server server, MikrotikDevice mikrotik, NetworkDevice networkDevice) {
    }

    private record AttemptDiagnostic(
            LocalDateTime attemptedAt,
            String source,
            String host,
            Integer port,
            long durationMs,
            boolean success,
            String errorType,
            String message,
            String resolvedInterface
    ) {
        private static AttemptDiagnostic success(String source,
                                                 String host,
                                                 Integer port,
                                                 long durationMs,
                                                 LocalDateTime attemptedAt,
                                                 String resolvedInterface) {
            return new AttemptDiagnostic(attemptedAt, source, host, port, durationMs, true, null, "success", resolvedInterface);
        }

        private static AttemptDiagnostic failure(String source,
                                                 String host,
                                                 Integer port,
                                                 long durationMs,
                                                 String message,
                                                 String errorType,
                                                 LocalDateTime attemptedAt) {
            return new AttemptDiagnostic(attemptedAt, source, host, port, durationMs, false, errorType, message, null);
        }

        private static AttemptDiagnostic configFailure(String source,
                                                       String host,
                                                       Integer port,
                                                       long durationMs,
                                                       String message,
                                                       LocalDateTime attemptedAt) {
            return new AttemptDiagnostic(attemptedAt, source, host, port, durationMs, false, "ConfigError", message, null);
        }

        private String summary() {
            String target = safeSummaryPart(source) + "@" + safeSummaryPart(host) + ":" + (port != null ? port : "-");
            if (success) {
                return target + " ok " + durationMs + "ms" + (resolvedInterface != null ? " iface=" + resolvedInterface : "");
            }
            return target + " fail " + durationMs + "ms" + (errorType != null ? " " + errorType : "") + (message != null ? " " + message : "");
        }

        private Map<String, Object> toMap() {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("attemptedAt", attemptedAt);
            item.put("source", source);
            item.put("host", host);
            item.put("port", port);
            item.put("durationMs", durationMs);
            item.put("success", success);
            item.put("errorType", errorType);
            item.put("message", message);
            item.put("resolvedInterface", resolvedInterface);
            item.put("summary", summary());
            return item;
        }

        private static String safeSummaryPart(String value) {
            return value != null && !value.isBlank() ? value : "-";
        }
    }

    private static final class CollectorState {
        private final Deque<TrafficSample> samples = new ArrayDeque<>();
        private final Deque<AttemptDiagnostic> recentAttempts = new ArrayDeque<>();
        private Long serverId;
        private String serverName;
        private String serverLocation;
        private Long mikrotikId;
        private String mikrotikName;
        private Long networkDeviceId;
        private String interfaceName;
        private String interfaceComment;
        private String interfaceStatus;
        private Integer interfaceSpeed;
        private String preferredInterfaceHint;
        private LocalDateTime lastAttemptAt;
        private LocalDateTime lastSuccessAt;
        private LocalDateTime lastFailureAt;
        private String lastError;
        private String configError;
        private String targetSource;
        private String targetHost;
        private Integer targetPort;
        private String transport;
        private List<Map<String, Object>> candidateTargets = List.of();
        private AttemptDiagnostic lastFailedAttempt;
        private int consecutiveFailures;
        private boolean bootstrapLoaded;

        private void addSample(MikrotikRouterOsApiClient.MikrotikLiveTrafficSnapshot snapshot) {
            interfaceName = snapshot.interfaceName();
            interfaceComment = snapshot.interfaceComment();
            interfaceStatus = snapshot.status();
            interfaceSpeed = snapshot.speed();
            addSample(new TrafficSample(
                    snapshot.collectedAt(),
                    snapshot.rxBps(),
                    snapshot.txBps(),
                    snapshot.rxPackets(),
                    snapshot.txPackets(),
                    snapshot.rxErrors(),
                    snapshot.txErrors()
            ));
        }

        private void addSample(TrafficSample sample) {
            samples.addLast(sample);
            while (samples.size() > MAX_BUFFER_SIZE) {
                samples.removeFirst();
            }
        }

        private void addAttempt(AttemptDiagnostic attempt) {
            recentAttempts.addLast(attempt);
            if (!attempt.success()) {
                lastFailedAttempt = attempt;
                lastFailureAt = attempt.attemptedAt();
            }
            while (recentAttempts.size() > MAX_ATTEMPT_HISTORY) {
                recentAttempts.removeFirst();
            }
        }

        private void recordAttempts(List<AttemptDiagnostic> attempts) {
            for (AttemptDiagnostic attempt : attempts) {
                addAttempt(attempt);
            }
        }
    }
}
