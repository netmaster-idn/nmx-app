package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.model.AcsDevice;
import com.netmaster.nmx.model.AcsSettings;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.repository.AcsDeviceRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import com.netmaster.nmx.service.GenieAcsService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/network/acs")
@RequiredArgsConstructor
public class AcsController {

    private final AcsDeviceRepository acsRepository;
    private final GenieAcsService genieAcsService;
    private final CustomerServiceEntityRepository customerServiceEntityRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AcsDevice>>> getAllDevices() {
        return ResponseEntity.ok(ApiResponse.success("Data ACS berhasil diambil", acsRepository.findAll()));
    }

    @GetMapping("/device/{id}")
    public ResponseEntity<ApiResponse<AcsDevice>> getDevice(@PathVariable Long id) {
        return acsRepository.findById(id)
                .map(device -> ResponseEntity.ok(ApiResponse.success("ACS ditemukan", device)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("ACS tidak ditemukan")));
    }

    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<AcsSettingsResponse>> getSettings() {
        try {
            AcsSettings settings = genieAcsService.getOrCreateSettings();
            return ResponseEntity.ok(ApiResponse.success("Setting ACS berhasil diambil", toSettingsResponse(settings)));
        } catch (Exception ex) {
            return ResponseEntity.ok(ApiResponse.success(
                    "Setting ACS belum tersedia, memakai default kosong",
                    new AcsSettingsResponse(null, null, false, false, "unavailable", null, null)
            ));
        }
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<AcsSettingsResponse>> updateSettings(@RequestBody AcsSettingsRequest request) {
        try {
            AcsSettings saved = genieAcsService.saveSettings(
                    request.serverUrl(),
                    request.username(),
                    request.password(),
                    request.active() == null || request.active()
            );
            return ResponseEntity.ok(ApiResponse.success("Setting ACS berhasil disimpan", toSettingsResponse(saved)));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/settings/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testConnection() {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Tes koneksi ACS berhasil",
                    genieAcsService.testConnection()
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/live")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getLiveDevices() {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Data live device dari GenieACS berhasil diambil",
                    genieAcsService.getLiveDevices()
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/devices/{serialNumber}/detail")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getLiveDeviceDetail(@PathVariable String serialNumber) {
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Detail device ACS berhasil diambil",
                    genieAcsService.getLiveDeviceDetail(serialNumber)
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<List<AcsDevice>>> syncDevices() {
        try {
            List<AcsDevice> devices = genieAcsService.syncDevices();
            return ResponseEntity.ok(ApiResponse.success("Sinkronisasi ACS berhasil", devices));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/devices/{serialNumber}/reboot")
    public ResponseEntity<ApiResponse<Void>> rebootDevice(@PathVariable String serialNumber) {
        try {
            genieAcsService.submitRebootBySerial(serialNumber);
            return ResponseEntity.ok(ApiResponse.success("Perintah reboot berhasil dikirim ke GenieACS", null));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/devices/{serialNumber}/wifi")
    public ResponseEntity<ApiResponse<Void>> updateWifi(
            @PathVariable String serialNumber,
            @RequestBody AcsWifiUpdateRequest request
    ) {
        if (!StringUtils.hasText(request.wifiName()) || !StringUtils.hasText(request.wifiPassword())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Nama WiFi dan password wajib diisi"));
        }

        try {
            genieAcsService.submitWifiUpdateBySerial(serialNumber, request.wifiName().trim(), request.wifiPassword().trim());
            return ResponseEntity.ok(ApiResponse.success("Perintah update WiFi berhasil dikirim ke GenieACS", null));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PutMapping("/devices/{serialNumber}/wan")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateWanService(
            @PathVariable String serialNumber,
            @RequestBody AcsWanServiceUpdateRequest request
    ) {
        if (!StringUtils.hasText(request.connectionType())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Tipe koneksi WAN wajib diisi"));
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("mode", trimToNull(request.mode()));
            payload.put("instance", trimToNull(request.instance()));
            payload.put("connectionType", trimToNull(request.connectionType()));
            payload.put("enable", request.enable());
            payload.put("connectionName", trimToNull(request.connectionName()));
            payload.put("connectionMode", trimToNull(request.connectionMode()));
            payload.put("serviceList", trimToNull(request.serviceList()));
            payload.put("ipAddress", trimToNull(request.ipAddress()));
            payload.put("username", trimToNull(request.username()));
            payload.put("password", trimToNull(request.password()));
            payload.put("nat", request.nat());
            payload.put("vlanEnabled", request.vlanEnabled());
            payload.put("vlanId", trimToNull(request.vlanId()));
            payload.put("lanBind", trimToNull(request.lanBind()));
            payload.put("ipMode", trimToNull(request.ipMode()));
            payload.put("gateway", trimToNull(request.gateway()));
            payload.put("subnetMask", trimToNull(request.subnetMask()));
            payload.put("dnsServers", trimToNull(request.dnsServers()));
            genieAcsService.submitWanServiceUpdateBySerial(serialNumber, payload);
            return ResponseEntity.ok(ApiResponse.success(
                    "Perubahan WAN service berhasil dikirim ke GenieACS",
                    genieAcsService.getLiveDeviceDetail(serialNumber)
                ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PutMapping("/devices/{serialNumber}/ssid")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateSsid(
            @PathVariable String serialNumber,
            @RequestBody AcsSsidUpdateRequest request
    ) {
        if (!StringUtils.hasText(request.instance())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Instance SSID wajib diisi"));
        }
        if (!StringUtils.hasText(request.ssid())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("SSID wajib diisi"));
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("instance", trimToNull(request.instance()));
            payload.put("enable", request.enable());
            payload.put("ssid", trimToNull(request.ssid()));
            payload.put("security", trimToNull(request.security()));
            payload.put("password", trimToNull(request.password()));
            payload.put("powerPercent", trimToNull(request.powerPercent()));
            payload.put("autoChannel", request.autoChannel());
            payload.put("channel", trimToNull(request.channel()));
            genieAcsService.submitSsidUpdateBySerial(serialNumber, payload);
            return ResponseEntity.ok(ApiResponse.success(
                    "Perubahan SSID berhasil dikirim ke GenieACS",
                    genieAcsService.getLiveDeviceDetail(serialNumber)
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PutMapping("/customer-services/{serviceId}/subscriber")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateSubscriber(
            @PathVariable Long serviceId,
            @RequestBody AcsSubscriberUpdateRequest request
    ) {
        String pppoeUsername = trimToNull(request.pppoeUsername());
        String pppoePassword = trimToNull(request.pppoePassword());
        String wifiName = trimToNull(request.wifiName());
        String wifiPassword = trimToNull(request.wifiPassword());
        String serialNumber = trimToNull(request.serialNumber());

        if (!StringUtils.hasText(pppoeUsername)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("PPPoE username wajib diisi"));
        }
        if (!StringUtils.hasText(pppoePassword)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("PPPoE password wajib diisi"));
        }
        if (!StringUtils.hasText(wifiName)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Nama WiFi wajib diisi"));
        }
        if (!StringUtils.hasText(wifiPassword)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Password WiFi wajib diisi"));
        }

        try {
            CustomerServiceEntity service = (StringUtils.hasText(serialNumber)
                    ? customerServiceEntityRepository.findDetailedByOntSerial(serialNumber).stream().findFirst()
                    : java.util.Optional.<CustomerServiceEntity>empty())
                    .filter(item -> item.getId().equals(serviceId))
                    .or(() -> customerServiceEntityRepository.findById(serviceId))
                    .orElse(null);
            if (service == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Data customer service tidak ditemukan"));
            }

            customerServiceEntityRepository.findByPppoeUsernameIgnoreCaseOrderByIdDesc(pppoeUsername).stream()
                    .filter(existing -> !existing.getId().equals(serviceId))
                    .findFirst()
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("PPPoE username sudah digunakan customer lain");
                    });

            service.setPppoeUsername(pppoeUsername);
            service.setPppoePassword(pppoePassword);
            service.setWifiName(wifiName);
            service.setWifiPassword(wifiPassword);
            CustomerServiceEntity saved = customerServiceEntityRepository.save(service);

            String syncStatus = "local_only";
            String syncMessage = "Detail ACS berhasil diperbarui";
            if (StringUtils.hasText(serialNumber) && genieAcsService.isConfigured()) {
                try {
                    genieAcsService.submitSubscriberUpdateBySerial(serialNumber, pppoeUsername, pppoePassword, wifiName, wifiPassword);
                    syncStatus = "submitted_to_acs";
                    syncMessage = "Detail ACS berhasil diperbarui dan dikirim ke GenieACS";
                } catch (Exception ex) {
                    syncStatus = "acs_failed";
                    syncMessage = "Data lokal diperbarui, tetapi gagal dikirim ke GenieACS: " + ex.getMessage();
                }
            }

            return ResponseEntity.ok(ApiResponse.success(syncMessage, genieAcsService.getLiveDeviceDetail(
                    StringUtils.hasText(serialNumber) ? serialNumber : saved.getOntSerial()
            )));
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error(ex.getMessage()));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getStats() {
        try {
            if (!genieAcsService.isConfigured()) {
                return ResponseEntity.ok(ApiResponse.success("Statistik ACS", Map.of(
                        "configured", false,
                        "total", 0,
                        "online", 0,
                        "offline", 0,
                        "provisioned", 0
                )));
            }

            return ResponseEntity.ok(ApiResponse.success("Statistik ACS", genieAcsService.getLiveStats()));
        } catch (Exception ex) {
            return ResponseEntity.ok(ApiResponse.success("Statistik ACS", Map.of(
                    "configured", false,
                    "total", 0,
                    "online", 0,
                    "offline", 0,
                    "provisioned", 0
            )));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<AcsDevice>>> searchDevices(@RequestParam String keyword) {
        List<AcsDevice> devices = acsRepository.findAll().stream()
                .filter(d -> containsIgnoreCase(d.getName(), keyword)
                        || containsIgnoreCase(d.getSerialNumber(), keyword)
                        || containsIgnoreCase(d.getMacAddress(), keyword)
                        || containsIgnoreCase(d.getVendor(), keyword)
                        || containsIgnoreCase(d.getModel(), keyword))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Hasil pencarian", devices));
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && keyword != null && value.toLowerCase().contains(keyword.toLowerCase());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private AcsSettingsResponse toSettingsResponse(AcsSettings settings) {
        return new AcsSettingsResponse(
                settings.getServerUrl(),
                settings.getUsername(),
                StringUtils.hasText(settings.getPassword()),
                settings.isActive(),
                settings.getLastConnectionStatus(),
                settings.getLastConnectionMessage(),
                settings.getLastConnectedAt()
        );
    }
}

record AcsSettingsRequest(
        String serverUrl,
        String username,
        String password,
        Boolean active
) {
}

record AcsSettingsResponse(
        String serverUrl,
        String username,
        boolean passwordConfigured,
        boolean active,
        String lastConnectionStatus,
        String lastConnectionMessage,
        java.time.LocalDateTime lastConnectedAt
) {
}

record AcsWifiUpdateRequest(
        String wifiName,
        String wifiPassword
) {
}

record AcsSubscriberUpdateRequest(
        String serialNumber,
        String pppoeUsername,
        String pppoePassword,
        String wifiName,
        String wifiPassword
) {
}

record AcsWanServiceUpdateRequest(
        String mode,
        String instance,
        String connectionType,
        Boolean enable,
        String connectionName,
        String connectionMode,
        String serviceList,
        String ipAddress,
        String username,
        String password,
        Boolean nat,
        Boolean vlanEnabled,
        String vlanId,
        String lanBind,
        String ipMode,
        String gateway,
        String subnetMask,
        String dnsServers
) {
}

record AcsSsidUpdateRequest(
        String instance,
        Boolean enable,
        String ssid,
        String security,
        String password,
        String powerPercent,
        Boolean autoChannel,
        String channel
) {
}
