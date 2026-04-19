package com.netmaster.nmx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Slf4j
public class MikrotikConnectionService {

    private static final int DEFAULT_API_PORT = 8728;
    private static final int DEFAULT_WINBOX_PORT = 8291;
    private static final int CONNECT_TIMEOUT_MS = 3000;

    public Map<String, Object> testConnection(String host) {
        return testConnection(host, DEFAULT_API_PORT, "API");
    }

    public Map<String, Object> testConnection(String host, int defaultPort, String connectionType) {
        ConnectionTarget target = resolveTarget(host, defaultPort);
        if (target == null) {
            throw new IllegalArgumentException("Alamat Mikrotik wajib diisi sebelum test koneksi");
        }

        InetAddress inetAddress = resolveHost(target.host());
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(inetAddress, target.port()), CONNECT_TIMEOUT_MS);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("reachable", true);
            result.put("host", target.host());
            result.put("resolvedIpAddress", inetAddress.getHostAddress());
            result.put("port", target.port());
            result.put("connectionType", connectionType);
            result.put("message", "Koneksi ke " + connectionType + " Mikrotik berhasil");
            return result;
        } catch (IOException ex) {
            log.warn("Failed to connect to Mikrotik {}:{} - {}", target.host(), target.port(), ex.getMessage());
            throw new IllegalArgumentException("Koneksi ke " + connectionType + " Mikrotik gagal. Pastikan alamat dapat dijangkau dan port " + target.port() + " terbuka.");
        }
    }

    public String extractHost(String value) {
        ConnectionTarget target = resolveTarget(value);
        return target != null ? target.host() : null;
    }

    public Integer extractPort(String value) {
        ConnectionTarget target = resolveTarget(value);
        return target != null ? target.port() : null;
    }

    public ConnectionTarget resolveTarget(String value) {
        return resolveTarget(value, DEFAULT_API_PORT);
    }

    public ConnectionTarget resolveTarget(String value, int defaultPort) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }

        int separatorIndex = normalized.lastIndexOf(':');
        if (separatorIndex <= 0 || separatorIndex == normalized.length() - 1 || normalized.indexOf(':') != separatorIndex) {
            return new ConnectionTarget(normalized, defaultPort);
        }

        String host = normalize(normalized.substring(0, separatorIndex));
        String portValue = normalize(normalized.substring(separatorIndex + 1));
        if (host == null || portValue == null) {
            throw new IllegalArgumentException("Format IP VPN Mikrotik harus `host` atau `host:port`.");
        }

        try {
            int port = Integer.parseInt(portValue);
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port Mikrotik harus di antara 1 sampai 65535.");
            }
            return new ConnectionTarget(host, port);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Port Mikrotik tidak valid.");
        }
    }

    public List<ResolvedTarget> resolveApiCandidates(String monitoringTarget,
                                                     String apiAddress,
                                                     String winboxAddress,
                                                     String vpnAddress,
                                                     String deviceIpAddress) {
        List<ResolvedTarget> candidates = new ArrayList<>();
        LinkedHashSet<String> dedupe = new LinkedHashSet<>();

        String preferredTarget = normalize(monitoringTarget);
        if ("vpn".equalsIgnoreCase(preferredTarget)) {
            addCandidate(candidates, dedupe, "vpn", vpnAddress, DEFAULT_API_PORT);
            addCandidate(candidates, dedupe, "api", apiAddress, DEFAULT_API_PORT);
            addCandidate(candidates, dedupe, "winbox", winboxAddress, DEFAULT_API_PORT);
        } else if ("winbox".equalsIgnoreCase(preferredTarget)) {
            addCandidate(candidates, dedupe, "winbox", winboxAddress, DEFAULT_API_PORT);
            addCandidate(candidates, dedupe, "api", apiAddress, DEFAULT_API_PORT);
            addCandidate(candidates, dedupe, "vpn", vpnAddress, DEFAULT_API_PORT);
        } else if ("api".equalsIgnoreCase(preferredTarget)) {
            addCandidate(candidates, dedupe, "api", apiAddress, DEFAULT_API_PORT);
            addCandidate(candidates, dedupe, "vpn", vpnAddress, DEFAULT_API_PORT);
            addCandidate(candidates, dedupe, "winbox", winboxAddress, DEFAULT_API_PORT);
        } else {
            addCandidate(candidates, dedupe, "vpn", vpnAddress, DEFAULT_API_PORT);
            addCandidate(candidates, dedupe, "api", apiAddress, DEFAULT_API_PORT);
            addCandidate(candidates, dedupe, "winbox", winboxAddress, DEFAULT_API_PORT);
        }
        addCandidate(candidates, dedupe, "ip", deviceIpAddress, DEFAULT_API_PORT);

        return candidates;
    }

    public List<ResolvedTarget> resolveSnmpCandidates(String monitoringTarget,
                                                      String vpnAddress,
                                                      String apiAddress,
                                                      String winboxAddress,
                                                      String deviceIpAddress,
                                                      int defaultPort) {
        List<ResolvedTarget> candidates = new ArrayList<>();
        LinkedHashSet<String> dedupe = new LinkedHashSet<>();

        String preferredTarget = normalize(monitoringTarget);
        if ("vpn".equalsIgnoreCase(preferredTarget)) {
            addCandidate(candidates, dedupe, "vpn", vpnAddress, defaultPort);
            addCandidate(candidates, dedupe, "api", apiAddress, defaultPort);
            addCandidate(candidates, dedupe, "winbox", winboxAddress, defaultPort);
        } else if ("winbox".equalsIgnoreCase(preferredTarget)) {
            addCandidate(candidates, dedupe, "winbox", winboxAddress, defaultPort);
            addCandidate(candidates, dedupe, "api", apiAddress, defaultPort);
            addCandidate(candidates, dedupe, "vpn", vpnAddress, defaultPort);
        } else if ("api".equalsIgnoreCase(preferredTarget)) {
            addCandidate(candidates, dedupe, "api", apiAddress, defaultPort);
            addCandidate(candidates, dedupe, "vpn", vpnAddress, defaultPort);
            addCandidate(candidates, dedupe, "winbox", winboxAddress, defaultPort);
        } else {
            addCandidate(candidates, dedupe, "vpn", vpnAddress, defaultPort);
            addCandidate(candidates, dedupe, "api", apiAddress, defaultPort);
            addCandidate(candidates, dedupe, "winbox", winboxAddress, defaultPort);
        }
        addCandidate(candidates, dedupe, "ip", deviceIpAddress, defaultPort);

        return candidates;
    }

    private void addCandidate(List<ResolvedTarget> candidates,
                              LinkedHashSet<String> dedupe,
                              String source,
                              String value,
                              int defaultPort) {
        ConnectionTarget target = resolveTarget(value, defaultPort);
        if (target == null) {
            return;
        }
        String key = source + "|" + target.host() + ":" + target.port();
        if (dedupe.add(key)) {
            candidates.add(new ResolvedTarget(source, target));
        }
    }

    private InetAddress resolveHost(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Alamat Mikrotik tidak valid atau tidak dapat di-resolve.");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ConnectionTarget(String host, int port) {
    }

    public record ResolvedTarget(String source, ConnectionTarget target) {
    }
}
