package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.MikrotikDeviceUpsertRequest;
import com.netmaster.nmx.model.MikrotikDevice;
import com.netmaster.nmx.repository.MikrotikDeviceRepository;
import com.netmaster.nmx.service.MikrotikApiService;
import com.netmaster.nmx.service.MikrotikMonitoringManager;
import com.netmaster.nmx.service.MikrotikMonitoringService;
import com.netmaster.nmx.service.MikrotikStatusPingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/network/mikrotik")
@RequiredArgsConstructor
public class MikrotikController {

    private final MikrotikDeviceRepository mikrotikRepository;
    private final MikrotikApiService mikrotikApiService;
    private final MikrotikStatusPingService mikrotikStatusPingService;
    private final MikrotikMonitoringService mikrotikMonitoringService;
    private final MikrotikMonitoringManager mikrotikMonitoringManager;

    @GetMapping({"", "/devices"})
    public ResponseEntity<ApiResponse<List<MikrotikDevice>>> getAllDevices(@RequestParam(required = false) String keyword) {
        List<MikrotikDevice> devices = hasText(keyword)
                ? mikrotikRepository.searchByKeyword(keyword.trim())
                : mikrotikRepository.findAll(Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
        return ResponseEntity.ok(ApiResponse.success("Data device berhasil diambil", devices));
    }

    @GetMapping({"/{id}", "/devices/{id}"})
    public ResponseEntity<ApiResponse<MikrotikDevice>> getDevice(@PathVariable Long id) {
        return mikrotikRepository.findById(id)
                .map(device -> ResponseEntity.ok(ApiResponse.success("Device ditemukan", device)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Device tidak ditemukan")));
    }

    @PostMapping({"", "/devices"})
    public ResponseEntity<ApiResponse<MikrotikDevice>> createDevice(@RequestBody MikrotikDeviceUpsertRequest request) {
        MikrotikDevice device = new MikrotikDevice();
        applyRequest(device, request, true);
        MikrotikDevice saved = mikrotikRepository.save(device);
        return ResponseEntity.ok(ApiResponse.success("Device berhasil dibuat", saved));
    }

    @PutMapping({"/{id}", "/devices/{id}"})
    public ResponseEntity<ApiResponse<MikrotikDevice>> updateDevice(@PathVariable Long id,
                                                                    @RequestBody MikrotikDeviceUpsertRequest request) {
        MikrotikDevice device = mikrotikRepository.findById(id).orElse(null);
        if (device == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Device tidak ditemukan"));
        }
        applyRequest(device, request, false);
        MikrotikDevice updated = mikrotikRepository.save(device);
        return ResponseEntity.ok(ApiResponse.success("Device berhasil diperbarui", updated));
    }

    @PostMapping({"/{id}/test-monitoring", "/{id}/test-snmp"})
    public ResponseEntity<ApiResponse<Map<String, Object>>> testMonitoringConnection(@PathVariable Long id) {
        MikrotikDevice device = requireDevice(id);
        MikrotikApiService.MonitoringSnapshot result = mikrotikApiService.collectMonitoringSnapshot(device);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reachable", result.reachable());
        payload.put("sysName", result.systemName());
        payload.put("uptimeSeconds", result.uptimeSeconds());
        payload.put("cpuLoad", result.cpuLoad());
        payload.put("interfaceCount", result.interfaces() != null ? result.interfaces().size() : 0);
        payload.put("checkedAt", LocalDateTime.now());
        return ResponseEntity.ok(ApiResponse.success("Test monitoring via Winbox/API berhasil", payload));
    }

    @PostMapping("/{id}/test-api")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testApiConnection(@PathVariable Long id) {
        MikrotikDevice device = requireDevice(id);
        return ResponseEntity.ok(ApiResponse.success("Test API berhasil", buildApiTestPayload(device)));
    }

    @PostMapping("/test-api")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testApiConnectionDraft(@RequestBody MikrotikDeviceUpsertRequest request) {
        MikrotikDevice device = new MikrotikDevice();
        applyRequest(device, request, true);
        return ResponseEntity.ok(ApiResponse.success("Test API berhasil", buildApiTestPayload(device)));
    }

    @PostMapping("/check-status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkStatusDraft(@RequestBody MikrotikDeviceUpsertRequest request) {
        MikrotikDevice device = new MikrotikDevice();
        applyRequest(device, request, true);
        Map<String, Object> payload = buildStatusPayload(device, mikrotikApiService.checkReachability(device));
        return ResponseEntity.ok(ApiResponse.success("Status device berhasil diperiksa", payload));
    }

    private Map<String, Object> buildApiTestPayload(MikrotikDevice device) {
        MikrotikApiService.ApiTestResult result = mikrotikApiService.testConnection(device);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("identityName", result.identityName());
        payload.put("routerOsVersion", result.routerOsVersion());
        payload.put("boardName", result.boardName());
        payload.put("checkedAt", result.checkedAt());
        return payload;
    }

    @PostMapping("/{id}/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncNow(@PathVariable Long id) {
        requireDevice(id);
        mikrotikMonitoringManager.syncNow(id);
        MikrotikDevice refreshed = requireDevice(id);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("lastMonitoringSyncAt", refreshed.getLastSnmpSyncAt());
        payload.put("lastApiSyncAt", refreshed.getLastApiSyncAt());
        payload.put("currentStatus", refreshed.resolveCurrentStatus());
        return ResponseEntity.ok(ApiResponse.success("Sinkronisasi manual berhasil dijalankan", payload));
    }

    @PostMapping("/{id}/check-status")
    public ResponseEntity<ApiResponse<MikrotikDevice>> checkStatus(@PathVariable Long id) {
        MikrotikDevice device = mikrotikStatusPingService.refreshDeviceStatus(id);
        return ResponseEntity.ok(ApiResponse.success("Status device berhasil diperbarui", device));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDevice(@PathVariable Long id) {
        if (!mikrotikRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Device tidak ditemukan"));
        }
        mikrotikRepository.deleteById(id);
        mikrotikMonitoringService.deactivateSynchronizedDevice(id);
        return ResponseEntity.ok(ApiResponse.success("Device berhasil dihapus", null));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<MikrotikDevice>>> searchDevices(@RequestParam String keyword) {
        return getAllDevices(keyword);
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getStats() {
        List<MikrotikDevice> all = mikrotikRepository.findAll();
        long total = all.size();
        long online = all.stream().filter(device -> "online".equalsIgnoreCase(device.resolveCurrentStatus())).count();
        long offline = total - online;
        return ResponseEntity.ok(ApiResponse.success("Statistik device", Map.of(
                "total", total,
                "online", online,
                "offline", offline
        )));
    }

    private void applyRequest(MikrotikDevice target, MikrotikDeviceUpsertRequest source, boolean create) {
        validateIp(source.getIpAddress(), "IP address");
        validateVpnEndpoint(source.getVpnIpAddress(), "VPN / Private IP");
        validateIp(source.getWinboxIp(), "Winbox IP");
        validatePort(source.getApiPort(), "API port");
        validateInterval(source.getMonitoringInterval(), "Monitoring interval", 15);
        validateInterval(source.getPollingIntervalSnmp(), "Monitoring interval", 15);
        validateInterval(source.getSyncIntervalApi(), "Monitoring interval", 15);

        String resolvedVpnEndpoint = firstText(normalize(source.getVpnIpAddress()), target.resolveVpnEndpoint());
        String resolvedVpnHost = extractEndpointHost(resolvedVpnEndpoint);
        Integer resolvedVpnPort = firstPositive(
                extractEndpointPort(resolvedVpnEndpoint),
                source.getApiPort(),
                target.resolveVpnPort(),
                8728
        );
        Integer resolvedMonitoringInterval = firstPositive(
                source.getMonitoringInterval(),
                source.getPollingIntervalSnmp(),
                source.getSyncIntervalApi(),
                target.getMonitoringInterval(),
                60
        );
        String resolvedApiPassword = hasText(source.getApiPassword()) ? source.getApiPassword() : target.resolveApiPassword();
        String resolvedPrimaryIp = firstText(resolvedVpnHost, source.getIpAddress());
        if (Boolean.TRUE.equals(defaultBoolean(source.getApiEnabled(), true))
                && (!hasText(source.getApiUsername()) || !hasText(resolvedApiPassword))) {
            throw new IllegalArgumentException("API username dan password wajib jika API aktif.");
        }
        if (Boolean.TRUE.equals(defaultBoolean(source.getApiEnabled(), true))
                && !hasText(resolvedPrimaryIp)) {
            throw new IllegalArgumentException("VPN / Private IP atau IP address device wajib diisi agar backend bisa menjangkau MikroTik.");
        }

        target.applyDeviceName(firstText(source.getDeviceName(), target.resolveDeviceName(), "MikroTik Device"));
        target.applySiteName(firstText(source.getSiteName(), target.resolveSiteName()));
        target.setIpAddress(normalize(source.getIpAddress()));
        target.setVpnIpAddress(buildEndpoint(resolvedVpnHost, resolvedVpnPort));
        target.setApiIpAddress(firstText(resolvedVpnHost, normalize(source.getIpAddress())));
        target.setWinboxIpAddress(firstText(normalize(source.getWinboxIp()), target.getWinboxIpAddress()));
        target.setMonitoringTarget("vpn");
        target.setApiPort(resolvedVpnPort);
        target.applyApiCredentials(source.getApiUsername(), resolvedApiPassword);
        target.setDescription(normalize(source.getNotes()));
        target.setNotes(normalize(source.getNotes()));
        target.setPollingIntervalSnmp(resolvedMonitoringInterval);
        target.setSyncIntervalApi(resolvedMonitoringInterval);
        target.setSnmpEnabled(false);
        target.setApiEnabled(defaultBoolean(source.getApiEnabled(), true));
        target.setActive(defaultBoolean(source.getIsActive(), true));
        if (target.getCurrentStatus() == null) {
            target.applyCurrentStatus("offline");
        }
    }

    private Map<String, Object> buildStatusPayload(MikrotikDevice device, boolean reachable) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceName", device.resolveDeviceName());
        payload.put("siteName", device.resolveSiteName());
        payload.put("currentStatus", reachable ? "online" : "offline");
        payload.put("reachable", reachable);
        payload.put("checkedAt", LocalDateTime.now());
        payload.put("targetHost", device.resolveVpnHost());
        payload.put("targetPort", device.resolveVpnPort());
        return payload;
    }

    private MikrotikDevice requireDevice(Long id) {
        return mikrotikRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Device tidak ditemukan"));
    }

    private Boolean defaultBoolean(Boolean value, boolean fallback) {
        return value != null ? value : fallback;
    }

    private void validateVpnEndpoint(String value, String label) {
        if (!hasText(value)) {
            return;
        }
        String host = extractEndpointHost(value);
        Integer port = extractEndpointPort(value);
        if (!hasText(host) || port == null) {
            throw new IllegalArgumentException(label + " harus menggunakan format IP:port, misalnya 103.103.21.27:9437.");
        }
        validateIp(host, label);
        validatePort(port, label);
    }

    private void validateIp(String value, String label) {
        if (!hasText(value)) {
            return;
        }
        try {
            InetAddress.getByName(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException(label + " tidak valid.");
        }
    }

    private void validatePort(Integer value, String label) {
        if (value == null) {
            return;
        }
        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException(label + " harus di antara 1 sampai 65535.");
        }
    }

    private void validateInterval(Integer value, String label, int minValue) {
        if (value == null) {
            return;
        }
        if (value < minValue) {
            throw new IllegalArgumentException(label + " minimal " + minValue + " detik.");
        }
    }

    private Integer firstPositive(Integer... values) {
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalize(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String extractEndpointHost(String value) {
        String normalized = normalize(value);
        if (!hasText(normalized)) {
            return null;
        }
        int separatorIndex = normalized.lastIndexOf(':');
        if (separatorIndex <= 0 || separatorIndex == normalized.length() - 1
                || normalized.indexOf(':') != separatorIndex) {
            return normalized;
        }
        return normalize(normalized.substring(0, separatorIndex));
    }

    private Integer extractEndpointPort(String value) {
        String normalized = normalize(value);
        if (!hasText(normalized)) {
            return null;
        }
        int separatorIndex = normalized.lastIndexOf(':');
        if (separatorIndex <= 0 || separatorIndex == normalized.length() - 1
                || normalized.indexOf(':') != separatorIndex) {
            return null;
        }
        try {
            return Integer.parseInt(normalized.substring(separatorIndex + 1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String buildEndpoint(String host, Integer port) {
        return hasText(host) && port != null ? host.trim() + ":" + port : normalize(host);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
