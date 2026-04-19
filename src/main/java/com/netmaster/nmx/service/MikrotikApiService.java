package com.netmaster.nmx.service;

import com.netmaster.nmx.model.MikrotikDevice;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MikrotikApiService {

    private final MikrotikConnectionService mikrotikConnectionService;
    private final MikrotikRouterOsApiClient mikrotikRouterOsApiClient;

    public ApiTestResult testConnection(MikrotikDevice device) {
        MikrotikConnectionService.ConnectionTarget target = resolveTarget(device);
        MikrotikRouterOsApiClient.MikrotikApiIdentitySnapshot snapshot =
                mikrotikRouterOsApiClient.collectIdentitySnapshot(
                        target,
                        device.resolveApiUsername(),
                        device.resolveApiPassword()
                );
        return new ApiTestResult(snapshot.identityName(), snapshot.routerOsVersion(), snapshot.boardName(), snapshot.collectedAt());
    }

    public PppSyncSnapshot collectPppSnapshot(MikrotikDevice device) {
        MikrotikConnectionService.ConnectionTarget target = resolveTarget(device);
        List<MikrotikRouterOsApiClient.MikrotikPppSessionSnapshot> activeSessions =
                mikrotikRouterOsApiClient.collectPppActiveSessions(
                        target,
                        device.resolveApiUsername(),
                        device.resolveApiPassword()
                );
        Map<String, String> secretProfiles = mikrotikRouterOsApiClient.collectPppSecretProfiles(
                target,
                device.resolveApiUsername(),
                device.resolveApiPassword()
        );
        List<MikrotikRouterOsApiClient.MikrotikLogSnapshot> recentLogs = mikrotikRouterOsApiClient.collectRecentPppLogs(
                target,
                device.resolveApiUsername(),
                device.resolveApiPassword(),
                120
        );
        return new PppSyncSnapshot(activeSessions, secretProfiles, recentLogs, LocalDateTime.now());
    }

    public MonitoringSnapshot collectMonitoringSnapshot(MikrotikDevice device) {
        MikrotikConnectionService.ConnectionTarget target = resolveTarget(device);
        MikrotikRouterOsApiClient.MikrotikSnapshot snapshot = mikrotikRouterOsApiClient.collectSnapshot(
                target,
                device.resolveApiUsername(),
                device.resolveApiPassword()
        );

        List<MonitoringInterfaceSnapshot> interfaces = new ArrayList<>();
        for (int index = 0; index < snapshot.interfaces().size(); index++) {
            MikrotikRouterOsApiClient.MikrotikInterfaceSnapshot row = snapshot.interfaces().get(index);
            interfaces.add(new MonitoringInterfaceSnapshot(
                    index + 1,
                    row.name(),
                    row.type(),
                    row.comment(),
                    "up".equalsIgnoreCase(row.status()) ? "up" : "down",
                    row.status(),
                    row.rxBytes(),
                    row.txBytes()
            ));
        }

        String systemName = firstNonBlank(value(snapshot.identity(), "name"), device.resolveDeviceName());
        Integer cpuLoad = parseInteger(value(snapshot.resource(), "cpu-load"));
        Long memoryTotal = parseLong(value(snapshot.resource(), "total-memory"));
        Long memoryFree = parseLong(value(snapshot.resource(), "free-memory"));
        Long memoryUsed = memoryTotal != null && memoryFree != null ? Math.max(memoryTotal - memoryFree, 0L) : null;

        return new MonitoringSnapshot(
                systemName,
                true,
                parseDurationToSeconds(value(snapshot.resource(), "uptime")),
                cpuLoad,
                memoryTotal,
                memoryUsed,
                memoryFree,
                firstDecimal(snapshot.health(), "temperature", "cpu-temperature"),
                firstDecimal(snapshot.health(), "voltage"),
                firstNonBlank(value(snapshot.health(), "state"), value(snapshot.health(), "status"), "healthy"),
                firstNonBlank(value(snapshot.resource(), "version"), device.getRosVersion()),
                firstNonBlank(value(snapshot.resource(), "board-name"), value(snapshot.resource(), "platform"), device.getRouterboardVersion()),
                interfaces,
                snapshot.collectedAt()
        );
    }

    public boolean checkReachability(MikrotikDevice device) {
        MikrotikConnectionService.ConnectionTarget target = resolveTarget(device);
        try {
            mikrotikConnectionService.testConnection(target.host() + ":" + target.port(), target.port(), "MikroTik API");
            return true;
        } catch (Exception ex) {
            log.debug("MikroTik API reachability failed for {}: {}", device.resolveDeviceName(), ex.getMessage());
            return false;
        }
    }

    public MikrotikConnectionService.ConnectionTarget resolveTarget(MikrotikDevice device) {
        if (!Boolean.TRUE.equals(device.getApiEnabled())) {
            throw new IllegalArgumentException("API MikroTik belum diaktifkan pada device ini.");
        }
        if (!hasText(device.resolveApiUsername()) || !hasText(device.resolveApiPassword())) {
            throw new IllegalArgumentException("API username dan password wajib diisi.");
        }
        List<MikrotikConnectionService.ResolvedTarget> candidates = mikrotikConnectionService.resolveApiCandidates(
                device.getMonitoringTarget(),
                device.getApiIpAddress(),
                device.getWinboxIpAddress(),
                device.resolveVpnEndpoint(),
                device.getIpAddress()
        );
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("IP VPN/private atau IP address API MikroTik wajib diisi.");
        }
        return candidates.get(0).target();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String value(Map<String, String> values, String key) {
        if (values == null || key == null) {
            return null;
        }
        String raw = values.get(key);
        return hasText(raw) ? raw.trim() : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Integer parseInteger(String value) {
        Long parsed = parseLong(value);
        return parsed != null ? parsed.intValue() : null;
    }

    private Long parseLong(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace(",", ".").replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || ".".equals(normalized) || "-".equals(normalized)) {
            return null;
        }
        try {
            return BigDecimal.valueOf(Double.parseDouble(normalized)).longValue();
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal firstDecimal(Map<String, String> values, String... keys) {
        for (String key : keys) {
            BigDecimal parsed = parseDecimal(value(values, key));
            if (parsed != null) {
                return parsed;
            }
        }
        return null;
    }

    private BigDecimal parseDecimal(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace(",", ".").replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || ".".equals(normalized) || "-".equals(normalized)) {
            return null;
        }
        try {
            return BigDecimal.valueOf(Double.parseDouble(normalized));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseDurationToSeconds(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        long totalSeconds = 0L;
        StringBuilder number = new StringBuilder();

        for (char current : normalized.toCharArray()) {
            if (Character.isDigit(current)) {
                number.append(current);
                continue;
            }
            if (number.isEmpty()) {
                continue;
            }
            long parsed = Long.parseLong(number.toString());
            switch (current) {
                case 'w' -> totalSeconds += parsed * 604800L;
                case 'd' -> totalSeconds += parsed * 86400L;
                case 'h' -> totalSeconds += parsed * 3600L;
                case 'm' -> totalSeconds += parsed * 60L;
                case 's' -> totalSeconds += parsed;
                default -> {
                }
            }
            number.setLength(0);
        }
        return totalSeconds;
    }

    public record ApiTestResult(
            String identityName,
            String routerOsVersion,
            String boardName,
            LocalDateTime checkedAt
    ) {
    }

    public record PppSyncSnapshot(
            List<MikrotikRouterOsApiClient.MikrotikPppSessionSnapshot> activeSessions,
            Map<String, String> secretProfiles,
            List<MikrotikRouterOsApiClient.MikrotikLogSnapshot> recentLogs,
            LocalDateTime collectedAt
    ) {
    }

    public record MonitoringSnapshot(
            String systemName,
            boolean reachable,
            Long uptimeSeconds,
            Integer cpuLoad,
            Long memoryTotal,
            Long memoryUsed,
            Long memoryFree,
            BigDecimal temperature,
            BigDecimal voltage,
            String boardHealth,
            String routerOsVersion,
            String boardName,
            List<MonitoringInterfaceSnapshot> interfaces,
            LocalDateTime collectedAt
    ) {
    }

    public record MonitoringInterfaceSnapshot(
            Integer interfaceIndex,
            String interfaceName,
            String interfaceType,
            String comment,
            String adminStatus,
            String operStatus,
            Long inOctets,
            Long outOctets
    ) {
    }
}
