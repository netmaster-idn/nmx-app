package com.netmaster.nmx.service;

import com.netmaster.nmx.model.MikrotikDevice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class MikrotikSnmpService {

    private final MikrotikConnectionService mikrotikConnectionService;

    private static final String SYS_NAME_OID = "1.3.6.1.2.1.1.5.0";
    private static final String SYS_UPTIME_OID = "1.3.6.1.2.1.1.3.0";
    private static final String IF_NAME_OID = "1.3.6.1.2.1.2.2.1.2";
    private static final String IF_ADMIN_STATUS_OID = "1.3.6.1.2.1.2.2.1.7";
    private static final String IF_OPER_STATUS_OID = "1.3.6.1.2.1.2.2.1.8";
    private static final String IF_IN_OCTETS_OID = "1.3.6.1.2.1.2.2.1.10";
    private static final String IF_OUT_OCTETS_OID = "1.3.6.1.2.1.2.2.1.16";
    private static final String IF_HC_IN_OCTETS_OID = "1.3.6.1.2.1.31.1.1.1.6";
    private static final String IF_HC_OUT_OCTETS_OID = "1.3.6.1.2.1.31.1.1.1.10";

    private static final List<String> CPU_LOAD_OIDS = List.of(
            "1.3.6.1.4.1.14988.1.1.3.10.0",
            "1.3.6.1.2.1.25.3.3.1.2.1"
    );
    private static final List<String> MEMORY_TOTAL_OIDS = List.of(
            "1.3.6.1.4.1.14988.1.1.3.8.0"
    );
    private static final List<String> MEMORY_FREE_OIDS = List.of(
            "1.3.6.1.4.1.14988.1.1.3.9.0"
    );
    private static final List<String> TEMPERATURE_OIDS = List.of(
            "1.3.6.1.4.1.14988.1.1.3.100.1.3.1",
            "1.3.6.1.4.1.14988.1.1.3.100.1.3.2"
    );
    private static final List<String> VOLTAGE_OIDS = List.of(
            "1.3.6.1.4.1.14988.1.1.3.100.1.2.1"
    );

    public SnmpTestResult testConnection(MikrotikDevice device) {
        try (SnmpSession session = open(device)) {
            String sysName = session.getAsString(SYS_NAME_OID);
            Long uptime = session.getAsLong(SYS_UPTIME_OID);
            return new SnmpTestResult(
                    true,
                    sysName,
                    uptime != null ? uptime / 100L : null,
                    firstAvailable(session, CPU_LOAD_OIDS)
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("SNMP connection gagal: " + ex.getMessage(), ex);
        }
    }

    public DeviceSnmpSnapshot collectSnapshot(MikrotikDevice device) {
        try (SnmpSession session = open(device)) {
            LocalDateTime collectedAt = LocalDateTime.now();
            String host = resolveSnmpHost(device);
            String systemName = session.getAsString(SYS_NAME_OID);
            Long uptime = session.getAsLong(SYS_UPTIME_OID);
            Integer cpuLoad = toInteger(firstAvailable(session, CPU_LOAD_OIDS));
            Long memoryTotal = toLong(firstAvailable(session, MEMORY_TOTAL_OIDS));
            Long memoryFree = toLong(firstAvailable(session, MEMORY_FREE_OIDS));
            Long memoryUsed = memoryTotal != null && memoryFree != null ? Math.max(memoryTotal - memoryFree, 0L) : null;
            BigDecimal temperature = toDecimal(firstAvailable(session, TEMPERATURE_OIDS));
            BigDecimal voltage = toDecimal(firstAvailable(session, VOLTAGE_OIDS));

            Map<Integer, String> names = session.walkColumn(IF_NAME_OID);
            Map<Integer, String> adminStatuses = session.walkColumn(IF_ADMIN_STATUS_OID);
            Map<Integer, String> operStatuses = session.walkColumn(IF_OPER_STATUS_OID);
            Map<Integer, String> hcInOctets = session.walkColumn(IF_HC_IN_OCTETS_OID);
            Map<Integer, String> hcOutOctets = session.walkColumn(IF_HC_OUT_OCTETS_OID);
            Map<Integer, String> inOctets = session.walkColumn(IF_IN_OCTETS_OID);
            Map<Integer, String> outOctets = session.walkColumn(IF_OUT_OCTETS_OID);

            List<InterfaceSnmpSnapshot> interfaces = new ArrayList<>();
            for (Map.Entry<Integer, String> entry : names.entrySet()) {
                Integer index = entry.getKey();
                Long inbound = toLong(firstText(hcInOctets.get(index), inOctets.get(index)));
                Long outbound = toLong(firstText(hcOutOctets.get(index), outOctets.get(index)));
                interfaces.add(new InterfaceSnmpSnapshot(
                        index,
                        entry.getValue(),
                        normalizeStatus(adminStatuses.get(index)),
                        normalizeStatus(operStatuses.get(index)),
                        inbound,
                        outbound
                ));
            }

            // SNMP is the source of truth for device health and interface traffic.
            return new DeviceSnmpSnapshot(
                    host,
                    true,
                    systemName,
                    uptime != null ? uptime / 100L : null,
                    cpuLoad,
                    memoryTotal,
                    memoryUsed,
                    memoryFree,
                    temperature,
                    voltage,
                    voltage != null ? "voltage-ok" : null,
                    interfaces,
                    collectedAt
            );
        } catch (Exception ex) {
            throw new IllegalStateException("SNMP polling gagal: " + ex.getMessage(), ex);
        }
    }

    public boolean checkReachability(MikrotikDevice device) {
        try {
            testConnection(device);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private SnmpSession open(MikrotikDevice device) throws IOException {
        if (!Boolean.TRUE.equals(device.getSnmpEnabled())) {
            throw new IllegalArgumentException("SNMP belum diaktifkan pada device ini.");
        }
        if (!"2c".equalsIgnoreCase(defaultText(device.getSnmpVersion(), "2c"))) {
            throw new IllegalArgumentException("SNMP version minimal yang didukung saat ini adalah v2c.");
        }
        if (!hasText(device.getSnmpCommunity())) {
            throw new IllegalArgumentException("SNMP community wajib diisi.");
        }

        String host = resolveSnmpHost(device);
        int port = device.getSnmpPort() != null ? device.getSnmpPort() : 161;
        TransportMapping<UdpAddress> transport = new DefaultUdpTransportMapping();
        Snmp snmp = new Snmp(transport);
        transport.listen();

        CommunityTarget<UdpAddress> target = new CommunityTarget<>();
        target.setCommunity(new OctetString(device.getSnmpCommunity()));
        target.setAddress(new UdpAddress(host + "/" + port));
        target.setRetries(1);
        target.setTimeout(3000L);
        target.setVersion(SnmpConstants.version2c);
        return new SnmpSession(snmp, target);
    }

    private String resolveSnmpHost(MikrotikDevice device) {
        List<MikrotikConnectionService.ResolvedTarget> candidates = mikrotikConnectionService.resolveSnmpCandidates(
                device.getMonitoringTarget(),
                device.resolveVpnEndpoint(),
                device.getApiIpAddress(),
                device.getWinboxIpAddress(),
                device.getIpAddress(),
                device.getSnmpPort() != null ? device.getSnmpPort() : 161
        );
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("IP VPN/private atau IP address device wajib diisi untuk SNMP.");
        }
        return candidates.get(0).target().host();
    }

    private String firstAvailable(SnmpSession session, List<String> oids) {
        for (String oid : oids) {
            String value = session.getAsString(oid);
            if (hasText(value)) {
                return value;
            }
        }
        return null;
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

    private String defaultText(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Integer toInteger(String value) {
        Long parsed = toLong(value);
        if (parsed == null) {
            return null;
        }
        return parsed > Integer.MAX_VALUE ? Integer.MAX_VALUE : parsed.intValue();
    }

    private Long toLong(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("[^0-9\\-]", "");
        if (normalized.isBlank() || "-".equals(normalized)) {
            return null;
        }
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal toDecimal(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.replace(",", ".").replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || ".".equals(normalized) || "-".equals(normalized)) {
            return null;
        }
        try {
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeStatus(String value) {
        Integer code = toInteger(value);
        if (code == null) {
            return "unknown";
        }
        return switch (code) {
            case 1 -> "up";
            case 2 -> "down";
            case 3 -> "testing";
            default -> "unknown";
        };
    }

    public record SnmpTestResult(
            boolean reachable,
            String sysName,
            Long uptimeSeconds,
            String sampleValue
    ) {
    }

    public record DeviceSnmpSnapshot(
            String host,
            boolean reachable,
            String systemName,
            Long uptimeSeconds,
            Integer cpuLoad,
            Long memoryTotal,
            Long memoryUsed,
            Long memoryFree,
            BigDecimal temperature,
            BigDecimal voltage,
            String boardHealth,
            List<InterfaceSnmpSnapshot> interfaces,
            LocalDateTime collectedAt
    ) {
    }

    public record InterfaceSnmpSnapshot(
            Integer interfaceIndex,
            String interfaceName,
            String adminStatus,
            String operStatus,
            Long inOctets,
            Long outOctets
    ) {
    }

    private static final class SnmpSession implements AutoCloseable {

        private final Snmp snmp;
        private final CommunityTarget<UdpAddress> target;

        private SnmpSession(Snmp snmp, CommunityTarget<UdpAddress> target) {
            this.snmp = snmp;
            this.target = target;
        }

        String getAsString(String oid) {
            try {
                VariableBinding binding = get(oid);
                return binding != null && binding.getVariable() != null ? binding.getVariable().toString() : null;
            } catch (Exception ex) {
                log.debug("SNMP get {} gagal: {}", oid, ex.getMessage());
                return null;
            }
        }

        Long getAsLong(String oid) {
            String value = getAsString(oid);
            if (value == null) {
                return null;
            }
            try {
                return Long.parseLong(value.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        Map<Integer, String> walkColumn(String oid) {
            Map<Integer, String> values = new LinkedHashMap<>();
            TreeUtils treeUtils = new TreeUtils(snmp, new DefaultPDUFactory(PDU.GETNEXT));
            List<TreeEvent> events = treeUtils.getSubtree(target, new OID(oid));
            if (events == null) {
                return values;
            }
            for (TreeEvent event : events) {
                if (event == null || event.isError() || event.getVariableBindings() == null) {
                    continue;
                }
                for (VariableBinding binding : event.getVariableBindings()) {
                    int index = binding.getOid().last();
                    values.put(index, binding.getVariable().toString());
                }
            }
            return values;
        }

        private VariableBinding get(String oid) throws IOException {
            PDU pdu = new PDU();
            pdu.add(new VariableBinding(new OID(oid)));
            pdu.setType(PDU.GET);
            ResponseEvent<?> response = snmp.send(pdu, target);
            if (response == null || response.getResponse() == null || response.getResponse().size() == 0) {
                throw new IOException("SNMP timeout");
            }
            return response.getResponse().get(0);
        }

        @Override
        public void close() throws IOException {
            snmp.close();
        }
    }
}
