package com.netmaster.nmx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Slf4j
public class MikrotikRouterOsApiClient {

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int SOCKET_TIMEOUT_MS = 20_000;

    public MikrotikSnapshot collectSnapshot(MikrotikConnectionService.ConnectionTarget target, String username, String password) {
        try (RouterOsSession session = RouterOsSession.open(target)) {
            session.login(username, password);

            Map<String, String> identity = session.executeFirst("/system/identity/print");
            Map<String, String> resource = session.executeFirst("/system/resource/print",
                    "=.proplist=name,platform,board-name,version,uptime,cpu-load,free-memory,total-memory,architecture-name");
            Map<String, String> health = session.executeFirstSafe("/system/health/print");

            List<Map<String, String>> rawInterfaces = session.execute("/interface/print",
                    "=.proplist=name,type,running,disabled,comment,rx-byte,tx-byte,rx-packet,tx-packet,rx-error,tx-error,speed,actual-speed");
            List<MikrotikInterfaceSnapshot> interfaces = new ArrayList<>();
            for (Map<String, String> rawInterface : rawInterfaces) {
                MikrotikInterfaceSnapshot snapshot = buildInterfaceSnapshot(rawInterface);
                if (snapshot != null) {
                    interfaces.add(snapshot);
                }
            }

            int activePppoe = session.executeSafe("/ppp/active/print").size();
            int activeHotspot = session.executeSafe("/ip/hotspot/active/print").size();

            return new MikrotikSnapshot(
                    identity,
                    resource,
                    health,
                    interfaces,
                    activePppoe,
                    activeHotspot,
                    LocalDateTime.now()
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal terhubung ke API MikroTik: " + ex.getMessage(), ex);
        }
    }

    public List<MikrotikActiveUserSnapshot> collectActiveUsers(MikrotikConnectionService.ConnectionTarget target,
                                                               String username,
                                                               String password) {
        try (RouterOsSession session = RouterOsSession.open(target)) {
            session.login(username, password);

            List<MikrotikActiveUserSnapshot> snapshots = new ArrayList<>();
            List<Map<String, String>> pppUsers = session.executeSafe("/ppp/active/print",
                    "=.proplist=name,address,caller-id,service,uptime,bytes-in,bytes-out");
            for (Map<String, String> row : pppUsers) {
                MikrotikActiveUserSnapshot snapshot = buildActiveUserSnapshot(session, row, "pppoe", row.get("name"));
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            }

            List<Map<String, String>> hotspotUsers = session.executeSafe("/ip/hotspot/active/print",
                    "=.proplist=user,address,mac-address,uptime,bytes-in,bytes-out");
            for (Map<String, String> row : hotspotUsers) {
                MikrotikActiveUserSnapshot snapshot = buildActiveUserSnapshot(session, row, "hotspot", row.get("user"));
                if (snapshot != null) {
                    snapshots.add(snapshot);
                }
            }

            return snapshots;
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal mengambil user aktif MikroTik: " + ex.getMessage(), ex);
        }
    }

    public MikrotikApiIdentitySnapshot collectIdentitySnapshot(MikrotikConnectionService.ConnectionTarget target,
                                                               String username,
                                                               String password) {
        try (RouterOsSession session = RouterOsSession.open(target)) {
            session.login(username, password);
            Map<String, String> identity = session.executeFirstSafe("/system/identity/print");
            Map<String, String> resource = session.executeFirstSafe("/system/resource/print",
                    "=.proplist=name,platform,board-name,version,uptime");
            return new MikrotikApiIdentitySnapshot(
                    value(identity, "name"),
                    value(resource, "version"),
                    firstNonBlank(value(resource, "board-name"), value(resource, "platform")),
                    LocalDateTime.now()
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal login ke API MikroTik: " + ex.getMessage(), ex);
        }
    }

    public List<MikrotikPppSessionSnapshot> collectPppActiveSessions(MikrotikConnectionService.ConnectionTarget target,
                                                                     String username,
                                                                     String password) {
        try (RouterOsSession session = RouterOsSession.open(target)) {
            session.login(username, password);
            List<Map<String, String>> rows = session.executeSafe("/ppp/active/print",
                    "=.proplist=.id,name,address,caller-id,service,uptime,session-id");
            List<MikrotikPppSessionSnapshot> snapshots = new ArrayList<>();
            for (Map<String, String> row : rows) {
                String name = value(row, "name");
                if (name == null) {
                    continue;
                }
                snapshots.add(new MikrotikPppSessionSnapshot(
                        name,
                        value(row, "address"),
                        value(row, "caller-id"),
                        value(row, "session-id"),
                        value(row, "service"),
                        parseDurationToSeconds(value(row, "uptime")),
                        row.toString()
                ));
            }
            return snapshots;
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal mengambil PPP active MikroTik: " + ex.getMessage(), ex);
        }
    }

    public Map<String, String> collectPppSecretProfiles(MikrotikConnectionService.ConnectionTarget target,
                                                        String username,
                                                        String password) {
        try (RouterOsSession session = RouterOsSession.open(target)) {
            session.login(username, password);
            Map<String, String> result = new LinkedHashMap<>();
            List<Map<String, String>> rows = session.executeSafe("/ppp/secret/print",
                    "=.proplist=name,profile");
            for (Map<String, String> row : rows) {
                String name = value(row, "name");
                if (name != null) {
                    result.put(name, value(row, "profile"));
                }
            }
            return result;
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal mengambil PPP secret MikroTik: " + ex.getMessage(), ex);
        }
    }

    public List<MikrotikLogSnapshot> collectRecentPppLogs(MikrotikConnectionService.ConnectionTarget target,
                                                          String username,
                                                          String password,
                                                          int limit) {
        try (RouterOsSession session = RouterOsSession.open(target)) {
            session.login(username, password);
            List<Map<String, String>> rows = session.executeSafe("/log/print",
                    "=.proplist=time,topics,message");
            List<MikrotikLogSnapshot> snapshots = new ArrayList<>();
            for (Map<String, String> row : rows) {
                String topics = value(row, "topics");
                String message = value(row, "message");
                if (!isRelevantPppLog(topics, message)) {
                    continue;
                }
                snapshots.add(new MikrotikLogSnapshot(
                        parseLogTime(value(row, "time")),
                        topics,
                        message,
                        row.toString()
                ));
            }
            return snapshots.stream()
                    .sorted(Comparator.comparing(MikrotikLogSnapshot::eventTime, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(Math.max(limit, 1))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal mengambil log PPPoE MikroTik: " + ex.getMessage(), ex);
        }
    }

    public MikrotikLiveTrafficSnapshot collectWanTraffic(MikrotikConnectionService.ConnectionTarget target,
                                                         String username,
                                                         String password) {
        return collectWanTraffic(target, username, password, null);
    }

    public MikrotikLiveTrafficSnapshot collectWanTraffic(MikrotikConnectionService.ConnectionTarget target,
                                                         String username,
                                                         String password,
                                                         String preferredInterfaceName) {
        try (RouterOsSession session = RouterOsSession.open(target)) {
            session.login(username, password);

            Map<String, String> identity = session.executeFirstSafe("/system/identity/print");
            List<Map<String, String>> rawInterfaces = session.execute("/interface/ethernet/print",
                    "=.proplist=name,type,running,disabled,comment,actual-speed,speed,rx-packet,tx-packet,rx-error,tx-error");
            if (rawInterfaces.isEmpty()) {
                rawInterfaces = session.execute("/interface/print",
                        "=.proplist=name,type,running,disabled,comment,actual-speed,speed,rx-packet,tx-packet,rx-error,tx-error");
            }
            Map<String, String> selectedInterface = selectWanInterface(rawInterfaces, preferredInterfaceName);
            if (selectedInterface == null) {
                throw new IllegalStateException("Interface WAN ethernet tidak ditemukan pada MikroTik");
            }

            String interfaceName = value(selectedInterface, "name");
            Map<String, String> traffic = session.executeFirstSafe("/interface/monitor-traffic",
                    "=interface=" + interfaceName,
                    "=once=");

            boolean disabled = Boolean.parseBoolean(selectedInterface.getOrDefault("disabled", "false"));
            boolean running = Boolean.parseBoolean(selectedInterface.getOrDefault("running", "false"));
            String status = disabled ? "down" : (running ? "up" : "down");

            return new MikrotikLiveTrafficSnapshot(
                    value(identity, "name"),
                    interfaceName,
                    value(selectedInterface, "comment"),
                    status,
                    parseInterfaceSpeed(firstNonBlank(selectedInterface.get("actual-speed"), selectedInterface.get("speed"))),
                    parseLong(firstNonBlank(traffic.get("rx-bits-per-second"), traffic.get("rx-bits-per-second-64"))),
                    parseLong(firstNonBlank(traffic.get("tx-bits-per-second"), traffic.get("tx-bits-per-second-64"))),
                    parseLong(firstNonBlank(traffic.get("rx-packets-per-second"), selectedInterface.get("rx-packet"))),
                    parseLong(firstNonBlank(traffic.get("tx-packets-per-second"), selectedInterface.get("tx-packet"))),
                    parseLong(firstNonBlank(traffic.get("rx-errors-per-second"), selectedInterface.get("rx-error"))),
                    parseLong(firstNonBlank(traffic.get("tx-errors-per-second"), selectedInterface.get("tx-error"))),
                    LocalDateTime.now()
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal mengambil trafik realtime MikroTik: " + ex.getMessage(), ex);
        }
    }

    public MikrotikMonitoringBundle collectMonitoringBundle(MikrotikConnectionService.ConnectionTarget target,
                                                            String username,
                                                            String password,
                                                            String preferredInterfaceName,
                                                            int logLimit) {
        try (RouterOsSession session = RouterOsSession.open(target)) {
            session.login(username, password);

            Map<String, String> identity = session.executeFirstSafe("/system/identity/print");
            Map<String, String> resource = session.executeFirstSafe("/system/resource/print",
                    "=.proplist=name,platform,board-name,version,uptime,cpu-load,free-memory,total-memory,architecture-name,cpu-count,cpu-frequency,free-hdd-space,total-hdd-space");

            List<Map<String, String>> rawInterfaces = session.executeSafe("/interface/print",
                    "=.proplist=name,type,running,disabled,comment,actual-speed,speed,rx-byte,tx-byte,rx-packet,tx-packet,rx-error,tx-error");
            Map<String, String> etherInterface = selectPreferredEtherInterface(rawInterfaces, preferredInterfaceName);
            MikrotikLiveTrafficSnapshot etherTraffic = null;
            if (etherInterface != null) {
                etherTraffic = buildLiveTrafficSnapshot(session, identity, etherInterface);
            }

            Map<String, String> profileMap = new LinkedHashMap<>();
            for (Map<String, String> secret : session.executeSafe("/ppp/secret/print", "=.proplist=name,profile")) {
                String name = value(secret, "name");
                if (name != null) {
                    profileMap.put(name, value(secret, "profile"));
                }
            }

            Map<String, Map<String, String>> interfaceByName = new LinkedHashMap<>();
            for (Map<String, String> rawInterface : rawInterfaces) {
                String name = value(rawInterface, "name");
                if (name != null) {
                    interfaceByName.put(name.toLowerCase(Locale.ROOT), rawInterface);
                }
            }

            List<MikrotikPppSessionMonitoringSnapshot> pppSessions = new ArrayList<>();
            for (Map<String, String> row : session.executeSafe("/ppp/active/print",
                    "=.proplist=.id,name,address,caller-id,service,uptime,session-id")) {
                String usernameValue = value(row, "name");
                if (usernameValue == null) {
                    continue;
                }
                Map<String, String> interfaceRow = resolvePppInterface(interfaceByName, usernameValue);
                pppSessions.add(new MikrotikPppSessionMonitoringSnapshot(
                        usernameValue,
                        value(row, "address"),
                        value(row, "caller-id"),
                        value(row, "service"),
                        value(row, "session-id"),
                        profileMap.get(usernameValue),
                        parseDurationToSeconds(value(row, "uptime")),
                        parseLong(interfaceRow != null ? interfaceRow.get("rx-byte") : null),
                        parseLong(interfaceRow != null ? interfaceRow.get("tx-byte") : null),
                        parseLong(interfaceRow != null ? interfaceRow.get("rx-packet") : null),
                        parseLong(interfaceRow != null ? interfaceRow.get("tx-packet") : null),
                        interfaceRow != null ? value(interfaceRow, "name") : usernameValue,
                        resolveInterfaceStatus(interfaceRow)
                ));
            }

            List<MikrotikLogSnapshot> logs = new ArrayList<>();
            for (Map<String, String> row : session.executeSafe("/log/print", "=.proplist=time,topics,message")) {
                String topics = value(row, "topics");
                String message = value(row, "message");
                if (!isRelevantMonitoringLog(topics, message)) {
                    continue;
                }
                logs.add(new MikrotikLogSnapshot(
                        parseLogTime(value(row, "time")),
                        topics,
                        message,
                        row.toString()
                ));
            }
            logs = logs.stream()
                    .sorted(Comparator.comparing(MikrotikLogSnapshot::eventTime, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(Math.max(logLimit, 1))
                    .toList();

            return new MikrotikMonitoringBundle(
                    value(identity, "name"),
                    value(resource, "version"),
                    firstNonBlank(value(resource, "board-name"), value(resource, "platform")),
                    value(resource, "architecture-name"),
                    parseDurationToSeconds(value(resource, "uptime")),
                    parseLong(value(resource, "free-memory")),
                    parseLong(value(resource, "total-memory")),
                    parseLong(value(resource, "free-hdd-space")),
                    parseLong(value(resource, "total-hdd-space")),
                    (int) parseLong(value(resource, "cpu-load")),
                    (int) parseLong(value(resource, "cpu-count")),
                    (int) parseLong(value(resource, "cpu-frequency")),
                    etherTraffic,
                    pppSessions,
                    logs,
                    LocalDateTime.now()
            );
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal mengambil bundle monitoring MikroTik: " + ex.getMessage(), ex);
        }
    }

    public void setPppSecretDisabled(MikrotikConnectionService.ConnectionTarget target,
                                     String username,
                                     String password,
                                     String pppoeUsername,
                                     boolean disabled) {
        try (RouterOsSession session = RouterOsSession.open(target)) {
            session.login(username, password);

            Map<String, String> secret = session.executeFirst("/ppp/secret/print",
                    "=.proplist=.id,name,disabled",
                    "?name=" + pppoeUsername);
            String secretId = value(secret, ".id");
            if (secretId == null) {
                throw new IllegalStateException("PPPoE secret " + pppoeUsername + " tidak ditemukan.");
            }

            session.executeCommand("/ppp/secret/set",
                    "=.id=" + secretId,
                    "=disabled=" + String.valueOf(disabled));
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal mengubah status PPPoE di MikroTik: " + ex.getMessage(), ex);
        }
    }

    public String getPppSecretStatus(MikrotikConnectionService.ConnectionTarget target,
                                     String username,
                                     String password,
                                     String pppoeUsername) {
        try (RouterOsSession session = RouterOsSession.open(target)) {
            session.login(username, password);

            Map<String, String> secret = session.executeFirst("/ppp/secret/print",
                    "=.proplist=.id,name,disabled",
                    "?name=" + pppoeUsername);
            String secretId = value(secret, ".id");
            if (secretId == null) {
                throw new IllegalStateException("PPPoE secret " + pppoeUsername + " tidak ditemukan.");
            }
            return Boolean.parseBoolean(secret.getOrDefault("disabled", "false")) ? "disabled" : "enabled";
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal mengambil status PPPoE di MikroTik: " + ex.getMessage(), ex);
        }
    }

    private MikrotikInterfaceSnapshot buildInterfaceSnapshot(Map<String, String> rawInterface) {
        String name = value(rawInterface, "name");
        if (name == null) {
            return null;
        }

        boolean disabled = Boolean.parseBoolean(rawInterface.getOrDefault("disabled", "false"));
        boolean running = Boolean.parseBoolean(rawInterface.getOrDefault("running", "false"));
        String status = disabled ? "down" : (running ? "up" : "down");

        long rxBytes = parseLong(rawInterface.get("rx-byte"));
        long txBytes = parseLong(rawInterface.get("tx-byte"));
        int speed = parseInterfaceSpeed(firstNonBlank(rawInterface.get("actual-speed"), rawInterface.get("speed")));

        return new MikrotikInterfaceSnapshot(
                name,
                value(rawInterface, "type"),
                value(rawInterface, "comment"),
                status,
                speed,
                rxBytes,
                txBytes,
                0L,
                0L,
                parseLong(rawInterface.get("rx-packet")),
                parseLong(rawInterface.get("tx-packet")),
                parseLong(rawInterface.get("rx-error")),
                parseLong(rawInterface.get("tx-error"))
        );
    }

    private Map<String, String> selectWanInterface(List<Map<String, String>> rawInterfaces, String preferredInterfaceName) {
        String preferredName = preferredInterfaceName != null && !preferredInterfaceName.isBlank()
                ? preferredInterfaceName.trim()
                : null;
        if (preferredName != null) {
            for (Map<String, String> rawInterface : rawInterfaces) {
                String name = value(rawInterface, "name");
                if (preferredName.equalsIgnoreCase(name)) {
                    return rawInterface;
                }
            }
        }

        Map<String, String> fallbackEther = null;

        for (Map<String, String> rawInterface : rawInterfaces) {
            String name = value(rawInterface, "name");
            String type = value(rawInterface, "type");
            if (name == null || !isEthernet(type, name)) {
                continue;
            }

            if (fallbackEther == null) {
                fallbackEther = rawInterface;
            }

            if (containsWanMarker(name) || containsWanMarker(rawInterface.get("comment"))) {
                return rawInterface;
            }
        }

        return fallbackEther;
    }

    private boolean isEthernet(String type, String name) {
        String normalizedType = type != null ? type.trim().toLowerCase(Locale.ROOT) : "";
        String normalizedName = name != null ? name.trim().toLowerCase(Locale.ROOT) : "";
        return normalizedType.contains("ether") || normalizedName.startsWith("ether");
    }

    private boolean containsWanMarker(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("wan");
    }

    private Map<String, String> selectPreferredEtherInterface(List<Map<String, String>> rawInterfaces, String preferredInterfaceName) {
        String preferredName = preferredInterfaceName != null && !preferredInterfaceName.isBlank()
                ? preferredInterfaceName.trim()
                : null;
        if (preferredName != null) {
            for (Map<String, String> rawInterface : rawInterfaces) {
                String name = value(rawInterface, "name");
                if (preferredName.equalsIgnoreCase(name)) {
                    return rawInterface;
                }
            }
        }
        for (Map<String, String> rawInterface : rawInterfaces) {
            String name = value(rawInterface, "name");
            if ("ether1".equalsIgnoreCase(name)) {
                return rawInterface;
            }
        }
        return selectWanInterface(rawInterfaces, preferredInterfaceName);
    }

    private MikrotikLiveTrafficSnapshot buildLiveTrafficSnapshot(RouterOsSession session,
                                                                 Map<String, String> identity,
                                                                 Map<String, String> selectedInterface) {
        String interfaceName = value(selectedInterface, "name");
        Map<String, String> traffic = session.executeFirstSafe("/interface/monitor-traffic",
                "=interface=" + interfaceName,
                "=once=");

        boolean disabled = Boolean.parseBoolean(selectedInterface.getOrDefault("disabled", "false"));
        boolean running = Boolean.parseBoolean(selectedInterface.getOrDefault("running", "false"));
        String status = disabled ? "down" : (running ? "up" : "down");

        return new MikrotikLiveTrafficSnapshot(
                value(identity, "name"),
                interfaceName,
                value(selectedInterface, "comment"),
                status,
                parseInterfaceSpeed(firstNonBlank(selectedInterface.get("actual-speed"), selectedInterface.get("speed"))),
                parseLong(firstNonBlank(traffic.get("rx-bits-per-second"), traffic.get("rx-bits-per-second-64"))),
                parseLong(firstNonBlank(traffic.get("tx-bits-per-second"), traffic.get("tx-bits-per-second-64"))),
                parseLong(firstNonBlank(traffic.get("rx-packets-per-second"), selectedInterface.get("rx-packet"))),
                parseLong(firstNonBlank(traffic.get("tx-packets-per-second"), selectedInterface.get("tx-packet"))),
                parseLong(firstNonBlank(traffic.get("rx-errors-per-second"), selectedInterface.get("rx-error"))),
                parseLong(firstNonBlank(traffic.get("tx-errors-per-second"), selectedInterface.get("tx-error"))),
                LocalDateTime.now()
        );
    }

    private Map<String, String> resolvePppInterface(Map<String, Map<String, String>> interfaceByName, String username) {
        if (username == null) {
            return null;
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        Map<String, String> direct = interfaceByName.get(normalized);
        if (direct != null) {
            return direct;
        }
        Map<String, String> wrapped = interfaceByName.get(("<" + normalized + ">"));
        if (wrapped != null) {
            return wrapped;
        }
        for (Map.Entry<String, Map<String, String>> entry : interfaceByName.entrySet()) {
            if (entry.getKey().contains(normalized)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String resolveInterfaceStatus(Map<String, String> interfaceRow) {
        if (interfaceRow == null) {
            return "unknown";
        }
        boolean disabled = Boolean.parseBoolean(interfaceRow.getOrDefault("disabled", "false"));
        boolean running = Boolean.parseBoolean(interfaceRow.getOrDefault("running", "false"));
        return disabled ? "disabled" : (running ? "active" : "inactive");
    }

    private MikrotikActiveUserSnapshot buildActiveUserSnapshot(RouterOsSession session,
                                                               Map<String, String> row,
                                                               String sourceType,
                                                               String interfaceName) {
        String username = value(row, "name");
        if (username == null) {
            username = value(row, "user");
        }
        if (username == null) {
            return null;
        }

        String ipAddress = value(row, "address");
        long downloadBps = parseLong(row.get("bytes-in"));
        long uploadBps = parseLong(row.get("bytes-out"));
        long uptimeSeconds = parseDurationToSeconds(row.get("uptime"));

        if (uptimeSeconds > 0) {
            downloadBps = downloadBps > 0 ? (downloadBps * 8) / uptimeSeconds : 0L;
            uploadBps = uploadBps > 0 ? (uploadBps * 8) / uptimeSeconds : 0L;
        }

        if ((downloadBps <= 0L && uploadBps <= 0L) && interfaceName != null && !interfaceName.isBlank()) {
            Map<String, String> traffic = session.executeFirstSafe("/interface/monitor-traffic",
                    "=interface=" + interfaceName,
                    "=once=");
            downloadBps = parseLong(firstNonBlank(traffic.get("rx-bits-per-second"), traffic.get("rx-bits-per-second-64")));
            uploadBps = parseLong(firstNonBlank(traffic.get("tx-bits-per-second"), traffic.get("tx-bits-per-second-64")));
        }

        return new MikrotikActiveUserSnapshot(
                username,
                ipAddress,
                value(row, "caller-id"),
                sourceType,
                interfaceName,
                Math.max(downloadBps, 0L),
                Math.max(uploadBps, 0L),
                uptimeSeconds
        );
    }

    private int parseInterfaceSpeed(String value) {
        long parsed = parseLong(value);
        if (parsed <= 0L) {
            return 0;
        }
        if (parsed > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) parsed;
    }

    private String value(Map<String, String> data, String key) {
        String value = data.get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    private boolean isRelevantPppLog(String topics, String message) {
        String haystack = ((topics != null ? topics : "") + " " + (message != null ? message : ""))
                .toLowerCase(Locale.ROOT);
        return haystack.contains("ppp") || haystack.contains("pppoe");
    }

    private boolean isRelevantMonitoringLog(String topics, String message) {
        String haystack = ((topics != null ? topics : "") + " " + (message != null ? message : ""))
                .toLowerCase(Locale.ROOT);
        return haystack.contains("ppp")
                || haystack.contains("pppoe")
                || haystack.contains("interface")
                || haystack.contains("ether")
                || haystack.contains("system")
                || haystack.contains("critical")
                || haystack.contains("error")
                || haystack.contains("warning");
    }

    private LocalDateTime parseLogTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        String normalized = value.trim();
        List<DateTimeFormatter> dateTimeFormats = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("MMM/dd HH:mm:ss", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("MMM/dd HH:mm:ss", Locale.getDefault())
        );
        for (DateTimeFormatter formatter : dateTimeFormats) {
            try {
                if (normalized.length() >= 11 && Character.isLetter(normalized.charAt(0))) {
                    return LocalDateTime.of(
                            LocalDate.now().withMonth(formatter.parse(normalized).get(java.time.temporal.ChronoField.MONTH_OF_YEAR))
                                    .withDayOfMonth(formatter.parse(normalized).get(java.time.temporal.ChronoField.DAY_OF_MONTH)),
                            LocalTime.from(formatter.parse(normalized))
                    );
                }
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ex) {
                // Try next known format.
            }
        }
        try {
            return LocalDateTime.of(LocalDate.now(), LocalTime.parse(normalized, DateTimeFormatter.ofPattern("HH:mm:ss")));
        } catch (DateTimeParseException ex) {
            return LocalDateTime.now();
        }
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        long multiplier = 1L;
        if (normalized.endsWith("gbps")) {
            multiplier = 1_000_000_000L;
        } else if (normalized.endsWith("mbps")) {
            multiplier = 1_000_000L;
        } else if (normalized.endsWith("kbps")) {
            multiplier = 1_000L;
        }

        normalized = normalized.replace(",", ".").replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || ".".equals(normalized) || "-".equals(normalized)) {
            return 0L;
        }
        try {
            return BigDecimal.valueOf(Double.parseDouble(normalized))
                    .multiply(BigDecimal.valueOf(multiplier))
                    .longValue();
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public record MikrotikSnapshot(
            Map<String, String> identity,
            Map<String, String> resource,
            Map<String, String> health,
            List<MikrotikInterfaceSnapshot> interfaces,
            int activePppoe,
            int activeHotspot,
            LocalDateTime collectedAt
    ) {
    }

    public record MikrotikInterfaceSnapshot(
            String name,
            String type,
            String comment,
            String status,
            int speed,
            long rxBytes,
            long txBytes,
            long rxBps,
            long txBps,
            long rxPackets,
            long txPackets,
            long rxErrors,
            long txErrors
    ) {
    }

    public record MikrotikActiveUserSnapshot(
            String username,
            String ipAddress,
            String callerId,
            String sourceType,
            String interfaceName,
            long downloadBps,
            long uploadBps,
            long uptimeSeconds
    ) {
    }

    public record MikrotikLiveTrafficSnapshot(
            String deviceName,
            String interfaceName,
            String interfaceComment,
            String status,
            int speed,
            long rxBps,
            long txBps,
            long rxPackets,
            long txPackets,
            long rxErrors,
            long txErrors,
            LocalDateTime collectedAt
    ) {
    }

    public record MikrotikApiIdentitySnapshot(
            String identityName,
            String routerOsVersion,
            String boardName,
            LocalDateTime collectedAt
    ) {
    }

    public record MikrotikPppSessionSnapshot(
            String username,
            String ipAddress,
            String callerId,
            String sessionId,
            String profileName,
            Long uptimeSeconds,
            String rawPayload
    ) {
    }

    public record MikrotikLogSnapshot(
            LocalDateTime eventTime,
            String topics,
            String message,
            String rawPayload
    ) {
    }

    public record MikrotikPppSessionMonitoringSnapshot(
            String username,
            String ipAddress,
            String callerId,
            String service,
            String sessionId,
            String profileName,
            long uptimeSeconds,
            long rxBytes,
            long txBytes,
            long rxPackets,
            long txPackets,
            String interfaceName,
            String status
    ) {
    }

    public record MikrotikMonitoringBundle(
            String identityName,
            String routerOsVersion,
            String boardName,
            String architectureName,
            long uptimeSeconds,
            long freeMemory,
            long totalMemory,
            long freeHdd,
            long totalHdd,
            int cpuLoad,
            int cpuCount,
            int cpuFrequency,
            MikrotikLiveTrafficSnapshot ether1Traffic,
            List<MikrotikPppSessionMonitoringSnapshot> pppSessions,
            List<MikrotikLogSnapshot> logs,
            LocalDateTime collectedAt
    ) {
    }

    private static final class RouterOsSession implements AutoCloseable {

        private final Socket socket;
        private final BufferedInputStream input;
        private final BufferedOutputStream output;

        private RouterOsSession(Socket socket) throws IOException {
            this.socket = socket;
            this.input = new BufferedInputStream(socket.getInputStream());
            this.output = new BufferedOutputStream(socket.getOutputStream());
        }

        static RouterOsSession open(MikrotikConnectionService.ConnectionTarget target) throws IOException {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress(target.host(), target.port()), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            return new RouterOsSession(socket);
        }

        void login(String username, String password) throws IOException {
            List<Sentence> modernLogin = talk("/login", "=name=" + username, "=password=" + password);
            if (isSuccessfulLogin(modernLogin)) {
                return;
            }

            close();
            throw new IOException("Autentikasi RouterOS gagal");
        }

        List<Map<String, String>> executeSafe(String command, String... words) {
            try {
                return execute(command, words);
            } catch (IOException ex) {
                log.debug("RouterOS command {} gagal: {}", command, ex.getMessage());
                return List.of();
            }
        }

        Map<String, String> executeFirstSafe(String command, String... words) {
            List<Map<String, String>> rows = executeSafe(command, words);
            return rows.isEmpty() ? Map.of() : rows.getFirst();
        }

        Map<String, String> executeFirst(String command, String... words) throws IOException {
            List<Map<String, String>> rows = execute(command, words);
            return rows.isEmpty() ? Map.of() : rows.getFirst();
        }

        List<Map<String, String>> execute(String command, String... words) throws IOException {
            List<Sentence> sentences = talk(command, words);
            List<Map<String, String>> rows = new ArrayList<>();
            for (Sentence sentence : sentences) {
                if ("!trap".equals(sentence.type())) {
                    throw new IOException(sentence.attributes().getOrDefault("message", "RouterOS mengembalikan error"));
                }
                if ("!re".equals(sentence.type())) {
                    rows.add(sentence.attributes());
                }
            }
            return rows;
        }

        void executeCommand(String command, String... words) throws IOException {
            for (Sentence sentence : talk(command, words)) {
                if ("!trap".equals(sentence.type())) {
                    throw new IOException(sentence.attributes().getOrDefault("message", "RouterOS mengembalikan error"));
                }
            }
        }

        private boolean isSuccessfulLogin(List<Sentence> sentences) {
            for (Sentence sentence : sentences) {
                if ("!trap".equals(sentence.type())) {
                    return false;
                }
                if ("!done".equals(sentence.type()) && !sentence.attributes().containsKey("ret")) {
                    return true;
                }
                if ("!done".equals(sentence.type()) && sentence.attributes().containsKey("ret")) {
                    return false;
                }
            }
            return false;
        }

        private List<Sentence> talk(String command, String... words) throws IOException {
            List<String> sentence = new ArrayList<>();
            sentence.add(command);
            if (words != null) {
                for (String word : words) {
                    if (word != null) {
                        sentence.add(word);
                    }
                }
            }

            writeSentence(sentence);
            return readReply();
        }

        private void writeSentence(List<String> words) throws IOException {
            for (String word : words) {
                writeWord(word);
            }
            writeLength(0);
            output.flush();
        }

        private void writeWord(String word) throws IOException {
            byte[] bytes = word.getBytes(StandardCharsets.UTF_8);
            writeLength(bytes.length);
            output.write(bytes);
        }

        private List<Sentence> readReply() throws IOException {
            List<Sentence> replies = new ArrayList<>();
            while (true) {
                Sentence sentence = readSentence();
                replies.add(sentence);
                if ("!done".equals(sentence.type()) || "!trap".equals(sentence.type())) {
                    return replies;
                }
            }
        }

        private Sentence readSentence() throws IOException {
            List<String> words = new ArrayList<>();
            while (true) {
                int length = readLength();
                if (length == 0) {
                    break;
                }
                byte[] wordBytes = input.readNBytes(length);
                if (wordBytes.length != length) {
                    throw new EOFException("RouterOS connection closed unexpectedly");
                }
                words.add(new String(wordBytes, StandardCharsets.UTF_8));
            }

            if (words.isEmpty()) {
                return new Sentence("!done", Map.of());
            }

            String type = words.getFirst();
            Map<String, String> attributes = new LinkedHashMap<>();
            for (int i = 1; i < words.size(); i++) {
                parseAttribute(words.get(i), attributes);
            }
            return new Sentence(type, attributes);
        }

        private void parseAttribute(String word, Map<String, String> attributes) {
            if (word == null || word.isBlank()) {
                return;
            }
            int startIndex = word.startsWith("=") ? 1 : 0;
            int separatorIndex = word.indexOf('=', startIndex);
            if (separatorIndex < 0) {
                attributes.put(word, "");
                return;
            }
            String key = word.substring(startIndex, separatorIndex);
            String value = word.substring(separatorIndex + 1);
            attributes.put(key, value);
        }

        private void writeLength(int length) throws IOException {
            if (length < 0x80) {
                output.write(length);
            } else if (length < 0x4000) {
                length |= 0x8000;
                output.write((length >> 8) & 0xFF);
                output.write(length & 0xFF);
            } else if (length < 0x200000) {
                length |= 0xC00000;
                output.write((length >> 16) & 0xFF);
                output.write((length >> 8) & 0xFF);
                output.write(length & 0xFF);
            } else if (length < 0x10000000) {
                length |= 0xE0000000;
                output.write((length >> 24) & 0xFF);
                output.write((length >> 16) & 0xFF);
                output.write((length >> 8) & 0xFF);
                output.write(length & 0xFF);
            } else {
                output.write(0xF0);
                output.write((length >> 24) & 0xFF);
                output.write((length >> 16) & 0xFF);
                output.write((length >> 8) & 0xFF);
                output.write(length & 0xFF);
            }
        }

        private int readLength() throws IOException {
            int first = input.read();
            if (first < 0) {
                throw new EOFException("RouterOS connection closed");
            }
            if ((first & 0x80) == 0x00) {
                return first;
            }
            if ((first & 0xC0) == 0x80) {
                int second = requireByte();
                return ((first & ~0xC0) << 8) + second;
            }
            if ((first & 0xE0) == 0xC0) {
                int second = requireByte();
                int third = requireByte();
                return ((first & ~0xE0) << 16) + (second << 8) + third;
            }
            if ((first & 0xF0) == 0xE0) {
                int second = requireByte();
                int third = requireByte();
                int fourth = requireByte();
                return ((first & ~0xF0) << 24) + (second << 16) + (third << 8) + fourth;
            }
            if ((first & 0xF8) == 0xF0) {
                return (requireByte() << 24) + (requireByte() << 16) + (requireByte() << 8) + requireByte();
            }
            throw new IOException("Panjang word RouterOS tidak valid");
        }

        private int requireByte() throws IOException {
            int value = input.read();
            if (value < 0) {
                throw new EOFException("RouterOS connection closed");
            }
            return value;
        }

        @Override
        public void close() throws IOException {
            output.close();
            input.close();
            socket.close();
        }
    }

    private long parseDurationToSeconds(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
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

    private record Sentence(String type, Map<String, String> attributes) {
    }
}
