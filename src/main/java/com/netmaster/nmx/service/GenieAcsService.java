package com.netmaster.nmx.service;

import com.netmaster.nmx.model.AcsDevice;
import com.netmaster.nmx.model.AcsSettings;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.repository.AcsDeviceRepository;
import com.netmaster.nmx.repository.AcsSettingsRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenieAcsService {

    private final AcsSettingsRepository acsSettingsRepository;
    private final AcsDeviceRepository acsDeviceRepository;
    private final CustomerServiceEntityRepository customerServiceEntityRepository;
    private final JdbcTemplate jdbcTemplate;

    public AcsSettings getOrCreateSettings() {
        ensureAcsSchema();
        try {
            return acsSettingsRepository.findById(AcsSettings.SINGLETON_ID)
                    .orElseGet(() -> acsSettingsRepository.save(new AcsSettings()));
        } catch (RuntimeException ex) {
            if (!isMissingAcsTable(ex)) {
                throw ex;
            }
            log.warn("ACS schema missing on first access, retrying schema bootstrap: {}", ex.getMessage());
            forceCreateAcsSchema();
            return acsSettingsRepository.findById(AcsSettings.SINGLETON_ID)
                    .orElseGet(() -> acsSettingsRepository.save(new AcsSettings()));
        }
    }

    public AcsSettings saveSettings(String serverUrl, String username, String password, boolean active) {
        AcsSettings settings = getOrCreateSettings();
        settings.setServerUrl(normalizeBaseUrl(serverUrl));
        settings.setUsername(trimToNull(username));
        if (StringUtils.hasText(password)) {
            settings.setPassword(password.trim());
        }
        settings.setActive(active);
        return acsSettingsRepository.save(settings);
    }

    public boolean isConfigured() {
        AcsSettings settings = getOrCreateSettings();
        return settings.isActive() && StringUtils.hasText(settings.getServerUrl());
    }

    public Map<String, Object> testConnection() {
        ensureConfigured();
        try {
            List<Map<String, Object>> devices = fetchRemoteDevices(1);
            AcsSettings settings = getOrCreateSettings();
            settings.setLastConnectionStatus("success");
            settings.setLastConnectionMessage("Koneksi ke GenieACS berhasil");
            settings.setLastConnectedAt(LocalDateTime.now());
            acsSettingsRepository.save(settings);
            return Map.of(
                    "status", "success",
                    "message", "Koneksi ke GenieACS berhasil",
                    "sampleCount", devices.size()
            );
        } catch (RuntimeException ex) {
            AcsSettings settings = getOrCreateSettings();
            settings.setLastConnectionStatus("failed");
            settings.setLastConnectionMessage(ex.getMessage());
            acsSettingsRepository.save(settings);
            throw ex;
        }
    }

    public List<Map<String, Object>> getLiveDevices() {
        ensureConfigured();
        List<Map<String, Object>> remoteDevices = fetchRemoteDevices(null);
        List<Map<String, Object>> mapped = new ArrayList<>();
        for (Map<String, Object> remoteDevice : remoteDevices) {
            Map<String, Object> summary = toDeviceSummary(remoteDevice);
            if (hasMatchedPppoeCustomer(summary)) {
                mapped.add(summary);
            }
        }
        return mapped;
    }

    public Map<String, Object> getLiveStats() {
        List<Map<String, Object>> liveDevices = getLiveDevices();
        long total = liveDevices.size();
        long online = liveDevices.stream()
                .filter(device -> "online".equalsIgnoreCase(Objects.toString(device.get("status"), null)))
                .count();
        long offline = liveDevices.stream()
                .filter(device -> "offline".equalsIgnoreCase(Objects.toString(device.get("status"), null)))
                .count();

        return Map.of(
                "configured", true,
                "total", total,
                "online", online,
                "offline", offline,
                "provisioned", total
        );
    }

    public Map<String, Object> getLiveDeviceDetail(String serialNumber) {
        if (!StringUtils.hasText(serialNumber)) {
            throw new IllegalArgumentException("Serial ONT belum tersedia");
        }
        Map<String, Object> remoteDevice = findRemoteDeviceBySerial(serialNumber.trim())
                .orElseThrow(() -> new IllegalArgumentException("Device ACS dengan serial tersebut tidak ditemukan"));
        return toDeviceDetail(remoteDevice);
    }

    public List<AcsDevice> syncDevices() {
        ensureAcsSchema();
        ensureConfigured();
        List<Map<String, Object>> remoteDevices = fetchRemoteDevices(null);
        List<AcsDevice> synced = new ArrayList<>();
        for (Map<String, Object> remoteDevice : remoteDevices) {
            AcsDevice device = upsertAcsDevice(remoteDevice);
            synced.add(device);
            syncCustomerService(device);
        }
        return synced;
    }

    public boolean submitRebootBySerial(String serialNumber) {
        if (!StringUtils.hasText(serialNumber)) {
            throw new IllegalArgumentException("Serial ONT belum tersedia");
        }
        Map<String, Object> remoteDevice = findRemoteDeviceBySerial(serialNumber.trim())
                .orElseThrow(() -> new IllegalArgumentException("Device ACS dengan serial tersebut tidak ditemukan"));
        String deviceId = Objects.toString(remoteDevice.get("_id"), null);
        postTask(deviceId, Map.of("name", "reboot"), true);
        return true;
    }

    public boolean submitWifiUpdateBySerial(String serialNumber, String wifiName, String wifiPassword) {
        if (!StringUtils.hasText(serialNumber)) {
            throw new IllegalArgumentException("Serial ONT belum tersedia");
        }
        Map<String, Object> remoteDevice = findRemoteDeviceBySerial(serialNumber.trim())
                .orElseThrow(() -> new IllegalArgumentException("Device ACS dengan serial tersebut tidak ditemukan"));
        String deviceId = Objects.toString(remoteDevice.get("_id"), null);

        List<List<Object>> parameterValues = new ArrayList<>();
        parameterValues.add(List.of("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID", wifiName, "xsd:string"));
        parameterValues.add(List.of("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.PreSharedKey.1.KeyPassphrase", wifiPassword, "xsd:string"));
        parameterValues.add(List.of("Device.WiFi.SSID.1.SSID", wifiName, "xsd:string"));
        parameterValues.add(List.of("Device.WiFi.AccessPoint.1.Security.KeyPassphrase", wifiPassword, "xsd:string"));

        postTask(deviceId, Map.of(
                "name", "setParameterValues",
                "parameterValues", parameterValues
        ), true);
        return true;
    }

    public boolean submitSubscriberUpdateBySerial(
            String serialNumber,
            String pppoeUsername,
            String pppoePassword,
            String wifiName,
            String wifiPassword
    ) {
        if (!StringUtils.hasText(serialNumber)) {
            throw new IllegalArgumentException("Serial ONT belum tersedia");
        }
        Map<String, Object> remoteDevice = findRemoteDeviceBySerial(serialNumber.trim())
                .orElseThrow(() -> new IllegalArgumentException("Device ACS dengan serial tersebut tidak ditemukan"));
        String deviceId = Objects.toString(remoteDevice.get("_id"), null);

        List<List<Object>> parameterValues = new ArrayList<>();
        if (StringUtils.hasText(pppoeUsername)) {
            parameterValues.add(List.of("InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.1.Username", pppoeUsername, "xsd:string"));
            parameterValues.add(List.of("Device.PPP.Interface.1.Username", pppoeUsername, "xsd:string"));
        }
        if (StringUtils.hasText(pppoePassword)) {
            parameterValues.add(List.of("InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.1.Password", pppoePassword, "xsd:string"));
            parameterValues.add(List.of("Device.PPP.Interface.1.Password", pppoePassword, "xsd:string"));
        }
        if (StringUtils.hasText(wifiName)) {
            parameterValues.add(List.of("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID", wifiName, "xsd:string"));
            parameterValues.add(List.of("Device.WiFi.SSID.1.SSID", wifiName, "xsd:string"));
        }
        if (StringUtils.hasText(wifiPassword)) {
            parameterValues.add(List.of("InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.PreSharedKey.1.KeyPassphrase", wifiPassword, "xsd:string"));
            parameterValues.add(List.of("Device.WiFi.AccessPoint.1.Security.KeyPassphrase", wifiPassword, "xsd:string"));
        }
        if (parameterValues.isEmpty()) {
            throw new IllegalArgumentException("Tidak ada perubahan yang dikirim ke ACS");
        }

        postTask(deviceId, Map.of(
                "name", "setParameterValues",
                "parameterValues", parameterValues
        ), true);
        return true;
    }

    public boolean submitWanServiceUpdateBySerial(String serialNumber, Map<String, Object> payload) {
        if (!StringUtils.hasText(serialNumber)) {
            throw new IllegalArgumentException("Serial ONT belum tersedia");
        }
        Map<String, Object> remoteDevice = findRemoteDeviceBySerial(serialNumber.trim())
                .orElseThrow(() -> new IllegalArgumentException("Device ACS dengan serial tersebut tidak ditemukan"));
        String deviceId = Objects.toString(remoteDevice.get("_id"), null);
        String mode = trimToNull(Objects.toString(payload.get("mode"), null));
        String connectionType = trimToNull(Objects.toString(payload.get("connectionType"), null));
        String instance = trimToNull(Objects.toString(payload.get("instance"), null));

        if (!StringUtils.hasText(connectionType) || (!"PPP".equalsIgnoreCase(connectionType) && !"IP".equalsIgnoreCase(connectionType))) {
            throw new IllegalArgumentException("Tipe WAN harus PPP atau IP");
        }

        String objectPrefix = "PPP".equalsIgnoreCase(connectionType)
                ? "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection"
                : "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection";

        if (!StringUtils.hasText(instance) && "create".equalsIgnoreCase(mode)) {
            instance = String.valueOf(resolveNextInstance(remoteDevice, objectPrefix));
            postTask(deviceId, Map.of(
                    "name", "addObject",
                    "objectName", objectPrefix + "."
            ), true);
        }

        if (!StringUtils.hasText(instance)) {
            throw new IllegalArgumentException("Instance WAN belum tersedia");
        }

        List<List<Object>> parameterValues = new ArrayList<>();
        String basePath = objectPrefix + "." + instance + ".";
        addParam(parameterValues, basePath + "Enable", payload.get("enable"), "xsd:boolean");
        addParam(parameterValues, basePath + "Name", payload.get("connectionName"), "xsd:string");
        addParam(parameterValues, basePath + "ConnectionType", payload.get("connectionMode"), "xsd:string");
        addParam(parameterValues, basePath + "X_FH_ServiceList", payload.get("serviceList"), "xsd:string");
        addParam(parameterValues, basePath + "NATEnabled", payload.get("nat"), "xsd:boolean");
        addParam(parameterValues, basePath + "X_FH_VLANEnabled", payload.get("vlanEnabled"), "xsd:boolean");
        addParam(parameterValues, basePath + "X_FH_VLANIDMark", payload.get("vlanId"), "xsd:unsignedInt");
        addParam(parameterValues, basePath + "X_FH_LanInterface", payload.get("lanBind"), "xsd:string");

        if ("PPP".equalsIgnoreCase(connectionType)) {
            addParam(parameterValues, basePath + "Username", payload.get("username"), "xsd:string");
            addParam(parameterValues, basePath + "Password", payload.get("password"), "xsd:string");
            addParam(parameterValues, basePath + "ExternalIPAddress", payload.get("ipAddress"), "xsd:string");
        } else {
            addParam(parameterValues, basePath + "AddressingType", payload.get("ipMode"), "xsd:string");
            addParam(parameterValues, basePath + "ExternalIPAddress", payload.get("ipAddress"), "xsd:string");
            addParam(parameterValues, basePath + "DefaultGateway", payload.get("gateway"), "xsd:string");
            addParam(parameterValues, basePath + "SubnetMask", payload.get("subnetMask"), "xsd:string");
            addParam(parameterValues, basePath + "DNSServers", payload.get("dnsServers"), "xsd:string");
        }

        if (parameterValues.isEmpty()) {
            throw new IllegalArgumentException("Tidak ada perubahan WAN yang dikirim ke ACS");
        }

        postTask(deviceId, Map.of(
                "name", "setParameterValues",
                "parameterValues", parameterValues
        ), true);
        return true;
    }

    public boolean submitSsidUpdateBySerial(String serialNumber, Map<String, Object> payload) {
        if (!StringUtils.hasText(serialNumber)) {
            throw new IllegalArgumentException("Serial ONT belum tersedia");
        }
        Map<String, Object> remoteDevice = findRemoteDeviceBySerial(serialNumber.trim())
                .orElseThrow(() -> new IllegalArgumentException("Device ACS dengan serial tersebut tidak ditemukan"));
        String deviceId = Objects.toString(remoteDevice.get("_id"), null);
        String instance = trimToNull(Objects.toString(payload.get("instance"), null));
        if (!StringUtils.hasText(instance)) {
            throw new IllegalArgumentException("Instance SSID belum tersedia");
        }

        List<List<Object>> parameterValues = new ArrayList<>();
        String wlanPath = "InternetGatewayDevice.LANDevice.1.WLANConfiguration." + instance + ".";
        addParam(parameterValues, wlanPath + "Enable", payload.get("enable"), "xsd:boolean");
        addParam(parameterValues, wlanPath + "SSID", payload.get("ssid"), "xsd:string");
        addParam(parameterValues, wlanPath + "Channel", payload.get("channel"), "xsd:unsignedInt");
        addParam(parameterValues, wlanPath + "AutoChannelEnable", payload.get("autoChannel"), "xsd:boolean");
        addParam(parameterValues, wlanPath + "X_FH_TxPowerPercent", payload.get("powerPercent"), "xsd:unsignedInt");
        applySecurityParams(parameterValues, wlanPath, trimToNull(Objects.toString(payload.get("security"), null)), payload.get("password"));

        String deviceSsidPath = "Device.WiFi.SSID." + instance + ".";
        addParam(parameterValues, deviceSsidPath + "Enable", payload.get("enable"), "xsd:boolean");
        addParam(parameterValues, deviceSsidPath + "SSID", payload.get("ssid"), "xsd:string");

        String deviceApPath = "Device.WiFi.AccessPoint." + instance + ".";
        addParam(parameterValues, deviceApPath + "Enable", payload.get("enable"), "xsd:boolean");
        addParam(parameterValues, deviceApPath + "Security.ModeEnabled", payload.get("security"), "xsd:string");
        addParam(parameterValues, deviceApPath + "Security.KeyPassphrase", payload.get("password"), "xsd:string");

        if (parameterValues.isEmpty()) {
            throw new IllegalArgumentException("Tidak ada perubahan SSID yang dikirim ke ACS");
        }

        postTask(deviceId, Map.of(
                "name", "setParameterValues",
                "parameterValues", parameterValues
        ), true);
        return true;
    }

    private void syncCustomerService(AcsDevice device) {
        if (!StringUtils.hasText(device.getSerialNumber())) {
            return;
        }
        Optional<CustomerServiceEntity> optionalService = customerServiceEntityRepository
                .findByOntSerialOrderByIdDesc(device.getSerialNumber())
                .stream()
                .findFirst();
        if (optionalService.isEmpty()) {
            return;
        }

        CustomerServiceEntity service = optionalService.get();
        if (device.getOpticalRxPower() != null) {
            service.setOntRedaman(BigDecimal.valueOf(device.getOpticalRxPower()));
        }
        if (StringUtils.hasText(device.getWifiName())) {
            service.setWifiName(device.getWifiName());
        }
        customerServiceEntityRepository.save(service);
    }

    private AcsDevice upsertAcsDevice(Map<String, Object> remoteDevice) {
        String remoteId = Objects.toString(remoteDevice.get("_id"), null);
        String serialNumber = firstNonBlank(
                extractString(remoteDevice, "_deviceId._SerialNumber"),
                extractString(remoteDevice, "VirtualParameters.getSerialNumber._value"),
                extractString(remoteDevice, "DeviceID.SerialNumber._value"),
                extractString(remoteDevice, "InternetGatewayDevice.DeviceInfo.SerialNumber._value"),
                extractString(remoteDevice, "Device.DeviceInfo.SerialNumber._value")
        );

        AcsDevice device = (StringUtils.hasText(remoteId)
                ? acsDeviceRepository.findByAcsDeviceId(remoteId)
                : Optional.<AcsDevice>empty())
                .or(() -> acsDeviceRepository.findBySerialNumber(serialNumber))
                .orElseGet(AcsDevice::new);

        device.setAcsDeviceId(remoteId);
        device.setName(firstNonBlank(
                extractString(remoteDevice, "DeviceID.ID._value"),
                serialNumber,
                remoteId,
                "ONT-" + System.nanoTime()
        ));
        device.setSerialNumber(serialNumber);
        device.setVendor(firstNonBlank(
                extractString(remoteDevice, "_deviceId._Manufacturer"),
                extractString(remoteDevice, "DeviceID.Manufacturer._value"),
                extractString(remoteDevice, "InternetGatewayDevice.DeviceInfo.Manufacturer._value"),
                extractString(remoteDevice, "Device.DeviceInfo.Manufacturer._value")
        ));
        device.setModel(firstNonBlank(
                extractString(remoteDevice, "_deviceId._ProductClass"),
                extractString(remoteDevice, "DeviceID.ProductClass._value"),
                extractString(remoteDevice, "InternetGatewayDevice.DeviceInfo.ProductClass._value"),
                extractString(remoteDevice, "Device.DeviceInfo.ProductClass._value"),
                extractString(remoteDevice, "InternetGatewayDevice.DeviceInfo.ModelName._value"),
                extractString(remoteDevice, "Device.DeviceInfo.ModelName._value")
        ));
        device.setMacAddress(firstNonBlank(
                extractString(remoteDevice, "InternetGatewayDevice.LANDevice.1.LANEthernetInterfaceConfig.1.MACAddress._value"),
                extractString(remoteDevice, "Device.Ethernet.Interface.1.MACAddress._value")
        ));
        device.setIpAddress(firstNonBlank(
                extractString(remoteDevice, "VirtualParameters.pppoeIP._value"),
                extractString(remoteDevice, "VirtualParameters.IPTR069._value"),
                extractString(remoteDevice, "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.ExternalIPAddress._value"),
                extractString(remoteDevice, "Device.IP.Interface.1.IPv4Address.1.IPAddress._value")
        ));
        device.setFirmwareVersion(firstNonBlank(
                extractString(remoteDevice, "InternetGatewayDevice.DeviceInfo.SoftwareVersion._value"),
                extractString(remoteDevice, "Device.DeviceInfo.SoftwareVersion._value")
        ));
        device.setSoftwareVersion(device.getFirmwareVersion());
        device.setStatus(resolveStatus(remoteDevice));
        device.setConnectionType(firstNonBlank(
                extractString(remoteDevice, "VirtualParameters.getponmode._value"),
                extractString(remoteDevice, "InternetGatewayDevice.WANDevice.1.WANCommonInterfaceConfig.WANAccessType._value"),
                extractString(remoteDevice, "Device.XPON.LinkType._value"),
                "GPON"
        ));
        device.setOpticalRxPower(firstNonNullDouble(
                extractDouble(remoteDevice, "VirtualParameters.RXPower._value"),
                extractDouble(remoteDevice, "InternetGatewayDevice.WANDevice.1.WANPONInterfaceConfig.RXPower._value"),
                extractDouble(remoteDevice, "Device.XPON.Interface.1.Stats.RXPower._value"),
                extractDouble(remoteDevice, "Device.Optical.Interface.1.RXPower._value")
        ));
        device.setOpticalTxPower(firstNonNullDouble(
                extractDouble(remoteDevice, "InternetGatewayDevice.WANDevice.1.WANPONInterfaceConfig.TXPower._value"),
                extractDouble(remoteDevice, "Device.XPON.Interface.1.Stats.TXPower._value"),
                extractDouble(remoteDevice, "Device.Optical.Interface.1.TXPower._value")
        ));
        device.setOpticalTemperature(firstNonNullDouble(
                extractDouble(remoteDevice, "InternetGatewayDevice.WANDevice.1.WANPONInterfaceConfig.Temperature._value"),
                extractDouble(remoteDevice, "Device.XPON.Interface.1.Stats.Temperature._value")
        ));
        device.setOpticalVoltage(firstNonNullDouble(
                extractDouble(remoteDevice, "InternetGatewayDevice.WANDevice.1.WANPONInterfaceConfig.Voltage._value"),
                extractDouble(remoteDevice, "Device.XPON.Interface.1.Stats.Voltage._value")
        ));
        device.setWifiName(firstNonBlank(
                extractString(remoteDevice, "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID._value"),
                extractString(remoteDevice, "Device.WiFi.SSID.1.SSID._value")
        ));
        device.setLastInform(extractDateTime(remoteDevice, "_lastInform"));
        device.setLastAcsRequest(LocalDateTime.now());
        device.setLastSyncedAt(LocalDateTime.now());
        device.setActive(true);
        device.setProvisioned(device.getLastInform() != null);

        return acsDeviceRepository.save(device);
    }

    private List<Map<String, Object>> fetchRemoteDevices(Integer limit) {
        AcsSettings settings = ensureConfigured();
        String path = "/devices";
        if (limit != null) {
            path += "?limit=" + limit;
        }

        List<?> response = requestWithFallback(
                settings,
                path,
                targetUrl -> RestClient.builder()
                        .defaultHeader(HttpHeaders.AUTHORIZATION, buildBasicAuth(settings))
                        .build()
                        .get()
                        .uri(targetUrl)
                        .retrieve()
                        .body(List.class)
        );

        List<Map<String, Object>> devices = new ArrayList<>();
        if (response != null) {
            for (Object item : response) {
                if (item instanceof Map<?, ?> map) {
                    devices.add((Map<String, Object>) map);
                }
            }
        }
        return devices;
    }

    private Optional<Map<String, Object>> findRemoteDeviceBySerial(String serialNumber) {
        Optional<AcsDevice> localDevice = acsDeviceRepository.findBySerialNumber(serialNumber);
        if (localDevice.isPresent() && StringUtils.hasText(localDevice.get().getAcsDeviceId())) {
            Optional<Map<String, Object>> byAcsId = fetchRemoteDeviceByAcsId(localDevice.get().getAcsDeviceId());
            if (byAcsId.isPresent()) {
                return byAcsId;
            }
        }

        return fetchRemoteDevices(null).stream()
                .filter(device -> serialNumber.equalsIgnoreCase(firstNonBlank(
                        extractString(device, "_deviceId._SerialNumber"),
                        extractString(device, "VirtualParameters.getSerialNumber._value"),
                        extractString(device, "DeviceID.SerialNumber._value"),
                        extractString(device, "InternetGatewayDevice.DeviceInfo.SerialNumber._value"),
                        extractString(device, "Device.DeviceInfo.SerialNumber._value")
                )))
                .findFirst();
    }

    private Optional<Map<String, Object>> fetchRemoteDeviceByAcsId(String acsDeviceId) {
        AcsSettings settings = ensureConfigured();
        String encodedId = UriUtils.encodePathSegment(acsDeviceId, StandardCharsets.UTF_8);
        String path = "/devices/" + encodedId;
        RestClientResponseException lastException = null;

        for (String baseUrl : candidateBaseUrls(settings.getServerUrl())) {
            String targetUrl = baseUrl + path;
            try {
                Map<?, ?> response = RestClient.builder()
                        .defaultHeader(HttpHeaders.AUTHORIZATION, buildBasicAuth(settings))
                        .build()
                        .get()
                        .uri(targetUrl)
                        .retrieve()
                        .body(Map.class);
                if (response instanceof Map<?, ?> map) {
                    return Optional.of((Map<String, Object>) map);
                }
                return Optional.empty();
            } catch (RestClientResponseException ex) {
                lastException = ex;
                int status = ex.getStatusCode().value();
                if (status == 404 || status == 405) {
                    continue;
                }
                throw mapRestClientException(ex, targetUrl);
            }
        }

        if (lastException != null
                && lastException.getStatusCode().value() != 404
                && lastException.getStatusCode().value() != 405) {
            throw mapRestClientException(lastException, settings.getServerUrl());
        }
        return Optional.empty();
    }

    private void postTask(String deviceId, Map<String, Object> payload, boolean connectionRequest) {
        if (!StringUtils.hasText(deviceId)) {
            throw new IllegalArgumentException("Device ID ACS tidak tersedia");
        }
        AcsSettings settings = ensureConfigured();
        String suffix = connectionRequest ? "?connection_request" : "";
        String path = "/devices/" + deviceId + "/tasks" + suffix;
        requestWithFallback(
                settings,
                path,
                targetUrl -> {
                    RestClient.builder()
                            .defaultHeader(HttpHeaders.AUTHORIZATION, buildBasicAuth(settings))
                            .build()
                            .post()
                            .uri(targetUrl)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(payload)
                            .retrieve()
                            .toBodilessEntity();
                    return null;
                }
        );
    }

    private String buildBasicAuth(AcsSettings settings) {
        String username = Objects.toString(settings.getUsername(), "");
        String password = Objects.toString(settings.getPassword(), "");
        String raw = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes());
    }

    private AcsSettings ensureConfigured() {
        AcsSettings settings = getOrCreateSettings();
        if (!settings.isActive() || !StringUtils.hasText(settings.getServerUrl())) {
            throw new IllegalStateException("Setting GenieACS belum dikonfigurasi");
        }
        return settings;
    }

    private Map<String, Object> toDeviceSummary(Map<String, Object> remoteDevice) {
        Map<String, Object> summary = new LinkedHashMap<>();
        String serialNumber = firstNonBlank(
                extractString(remoteDevice, "_deviceId._SerialNumber"),
                extractString(remoteDevice, "VirtualParameters.getSerialNumber._value"),
                extractString(remoteDevice, "DeviceID.SerialNumber._value"),
                extractString(remoteDevice, "InternetGatewayDevice.DeviceInfo.SerialNumber._value"),
                extractString(remoteDevice, "Device.DeviceInfo.SerialNumber._value")
        );
        String pppoeUsername = firstNonBlank(
                extractString(remoteDevice, "VirtualParameters.pppoeUsername._value"),
                extractString(remoteDevice, "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.1.Username._value"),
                extractString(remoteDevice, "Device.PPP.Interface.1.Username._value")
        );
        summary.put("acsDeviceId", Objects.toString(remoteDevice.get("_id"), null));
        summary.put("serialNumber", serialNumber);
        summary.put("vendor", firstNonBlank(
                extractString(remoteDevice, "_deviceId._Manufacturer"),
                extractString(remoteDevice, "DeviceID.Manufacturer._value"),
                extractString(remoteDevice, "InternetGatewayDevice.DeviceInfo.Manufacturer._value"),
                extractString(remoteDevice, "Device.DeviceInfo.Manufacturer._value")
        ));
        summary.put("model", firstNonBlank(
                extractString(remoteDevice, "_deviceId._ProductClass"),
                extractString(remoteDevice, "DeviceID.ProductClass._value"),
                extractString(remoteDevice, "InternetGatewayDevice.DeviceInfo.ProductClass._value"),
                extractString(remoteDevice, "InternetGatewayDevice.DeviceInfo.ModelName._value"),
                extractString(remoteDevice, "Device.DeviceInfo.ModelName._value")
        ));
        summary.put("firmwareVersion", firstNonBlank(
                extractString(remoteDevice, "InternetGatewayDevice.DeviceInfo.SoftwareVersion._value"),
                extractString(remoteDevice, "Device.DeviceInfo.SoftwareVersion._value")
        ));
        summary.put("macAddress", firstNonBlank(
                extractString(remoteDevice, "InternetGatewayDevice.LANDevice.1.LANEthernetInterfaceConfig.1.MACAddress._value"),
                extractString(remoteDevice, "Device.Ethernet.Interface.1.MACAddress._value")
        ));
        summary.put("status", resolveStatus(remoteDevice));
        summary.put("connectionType", firstNonBlank(
                extractString(remoteDevice, "VirtualParameters.getponmode._value"),
                extractString(remoteDevice, "InternetGatewayDevice.WANDevice.1.WANCommonInterfaceConfig.WANAccessType._value"),
                extractString(remoteDevice, "Device.XPON.LinkType._value"),
                "GPON"
        ));
        summary.put("ipAddress", firstNonBlank(
                extractString(remoteDevice, "VirtualParameters.pppoeIP._value"),
                extractString(remoteDevice, "VirtualParameters.IPTR069._value"),
                extractString(remoteDevice, "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection.1.ExternalIPAddress._value"),
                extractString(remoteDevice, "Device.IP.Interface.1.IPv4Address.1.IPAddress._value")
        ));
        summary.put("wifiName", firstNonBlank(
                extractString(remoteDevice, "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.SSID._value"),
                extractString(remoteDevice, "Device.WiFi.SSID.1.SSID._value")
        ));
        summary.put("opticalRxPower", firstNonNullDouble(
                extractDouble(remoteDevice, "VirtualParameters.RXPower._value"),
                extractDouble(remoteDevice, "InternetGatewayDevice.WANDevice.1.WANPONInterfaceConfig.RXPower._value"),
                extractDouble(remoteDevice, "Device.XPON.Interface.1.Stats.RXPower._value"),
                extractDouble(remoteDevice, "Device.Optical.Interface.1.RXPower._value")
        ));
        summary.put("lastInform", extractDateTime(remoteDevice, "_lastInform"));
        summary.put("pppoeUsername", pppoeUsername);
        enrichWithCustomerData(summary, pppoeUsername);
        return summary;
    }

    private Map<String, Object> toDeviceDetail(Map<String, Object> remoteDevice) {
        Map<String, Object> detail = new LinkedHashMap<>(toDeviceSummary(remoteDevice));
        detail.put("deviceInfo", buildDeviceInfo(remoteDevice, detail));
        detail.put("wanServices", buildWanServices(remoteDevice));
        detail.put("ssidList", buildSsidList(remoteDevice));
        detail.put("wifiClients", buildWifiClients(remoteDevice));
        return detail;
    }

    private void enrichWithCustomerData(Map<String, Object> summary, String pppoeUsername) {
        Optional<CustomerServiceEntity> optionalService = Optional.empty();
        if (StringUtils.hasText(pppoeUsername)) {
            optionalService = customerServiceEntityRepository
                    .findDetailedByPppoeUsername(pppoeUsername.trim())
                    .stream()
                    .findFirst();
        }

        if (optionalService.isEmpty()) {
            summary.put("customerServiceId", null);
            summary.put("customerId", null);
            summary.put("customerName", null);
            summary.put("regionName", null);
            summary.put("pppoePassword", null);
            summary.put("wifiPassword", null);
            return;
        }

        CustomerServiceEntity service = optionalService.get();
        summary.put("customerServiceId", service.getId());
        summary.put("customerId", service.getCustomer() != null ? service.getCustomer().getId() : null);
        summary.put("customerName", service.getCustomer() != null ? service.getCustomer().getFullName() : null);
        summary.put("regionName",
                service.getCustomer() != null && service.getCustomer().getRegion() != null
                        ? service.getCustomer().getRegion().getName()
                        : null
        );
        summary.put("pppoeUsername", firstNonBlank(pppoeUsername, service.getPppoeUsername()));
        summary.put("pppoePassword", service.getPppoePassword());
        summary.put("wifiName", firstNonBlank(Objects.toString(summary.get("wifiName"), null), service.getWifiName()));
        summary.put("wifiPassword", service.getWifiPassword());
    }

    private boolean hasMatchedPppoeCustomer(Map<String, Object> summary) {
        return summary.get("customerServiceId") != null
                && StringUtils.hasText(Objects.toString(summary.get("pppoeUsername"), null));
    }

    private Map<String, Object> buildDeviceInfo(Map<String, Object> remoteDevice, Map<String, Object> summary) {
        Map<String, Object> deviceInfo = new LinkedHashMap<>();
        deviceInfo.put("ping", resolvePingValue(remoteDevice));
        deviceInfo.put("deviceUptime", formatDuration(firstNonNullLong(
                extractLong(remoteDevice, "InternetGatewayDevice.DeviceInfo.UpTime._value"),
                extractLong(remoteDevice, "Device.DeviceInfo.UpTime._value"),
                extractLong(remoteDevice, "InternetGatewayDevice.DeviceInfo.X_CT-COM_StandbyTime._value")
        )));
        deviceInfo.put("pppUptime", formatDuration(firstNonNullLong(
                extractLong(remoteDevice, "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection.1.Uptime._value"),
                extractLong(remoteDevice, "Device.PPP.Interface.1.UpTime._value")
        )));
        deviceInfo.put("manufacturer", summary.get("vendor"));
        deviceInfo.put("deviceType", firstNonBlank(
                Objects.toString(summary.get("model"), null),
                extractString(remoteDevice, "InternetGatewayDevice.DeviceInfo.ModelName._value"),
                extractString(remoteDevice, "Device.DeviceInfo.ModelName._value")
        ));
        deviceInfo.put("snOnt", summary.get("serialNumber"));
        deviceInfo.put("macAddress", firstNonBlank(
                Objects.toString(summary.get("macAddress"), null),
                extractString(remoteDevice, "InternetGatewayDevice.LANDevice.1.LANEthernetInterfaceConfig.1.MACAddress._value"),
                extractString(remoteDevice, "Device.Ethernet.Interface.1.MACAddress._value")
        ));
        deviceInfo.put("softwareVersion", summary.get("firmwareVersion"));
        deviceInfo.put("linkType", firstNonBlank(
                extractString(remoteDevice, "VirtualParameters.getponmode._value"),
                extractString(remoteDevice, "InternetGatewayDevice.WANDevice.1.WANCommonInterfaceConfig.WANAccessType._value"),
                extractString(remoteDevice, "Device.XPON.LinkType._value"),
                Objects.toString(summary.get("connectionType"), null)
        ));
        deviceInfo.put("opticRxPower", summary.get("opticalRxPower"));
        deviceInfo.put("temperature", firstNonNullDouble(
                extractDouble(remoteDevice, "InternetGatewayDevice.WANDevice.1.WANPONInterfaceConfig.Temperature._value"),
                extractDouble(remoteDevice, "Device.XPON.Interface.1.Stats.Temperature._value")
        ));
        return deviceInfo;
    }

    private String resolvePingValue(Map<String, Object> remoteDevice) {
        String rawPing = firstNonBlank(
                extractString(remoteDevice, "VirtualParameters.Ping._value"),
                extractString(remoteDevice, "VirtualParameters.PingResponse._value"),
                extractString(remoteDevice, "InternetGatewayDevice.X_CT-COM_Ping.AverageResponseTime._value"),
                extractString(remoteDevice, "Device.IP.Diagnostics.IPPing.AverageResponseTime._value"),
                extractString(remoteDevice, "InternetGatewayDevice.X_FH_Ping.AverageResponseTime._value")
        );
        if (!StringUtils.hasText(rawPing)) {
            Double numericPing = firstNonNullDouble(
                    extractDouble(remoteDevice, "InternetGatewayDevice.X_CT-COM_Ping.AverageResponseTime._value"),
                    extractDouble(remoteDevice, "Device.IP.Diagnostics.IPPing.AverageResponseTime._value"),
                    extractDouble(remoteDevice, "InternetGatewayDevice.X_FH_Ping.AverageResponseTime._value")
            );
            if (numericPing != null) {
                return formatMsValue(numericPing);
            }
            return null;
        }

        try {
            return formatMsValue(Double.parseDouble(rawPing));
        } catch (NumberFormatException ignored) {
            return rawPing;
        }
    }

    private String formatMsValue(Double value) {
        if (value == null) {
            return null;
        }
        if (Math.abs(value - Math.rint(value)) < 0.001d) {
            return "%d ms".formatted(value.longValue());
        }
        return "%.1f ms".formatted(value);
    }

    private List<Map<String, Object>> buildWanServices(Map<String, Object> remoteDevice) {
        List<Map<String, Object>> services = new ArrayList<>();
        Map<String, Object> pppMap = extractMap(remoteDevice, "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANPPPConnection");
        services.addAll(parseWanObjects(pppMap, "PPP"));
        Map<String, Object> ipMap = extractMap(remoteDevice, "InternetGatewayDevice.WANDevice.1.WANConnectionDevice.1.WANIPConnection");
        services.addAll(parseWanObjects(ipMap, "IP"));
        services.sort(Comparator.comparing(item -> Objects.toString(item.get("instance"), "")));
        return services;
    }

    private List<Map<String, Object>> parseWanObjects(Map<String, Object> source, String connectionType) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (source == null) {
            return items;
        }

        source.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof Map<?, ?> && entry.getKey().matches("\\d+"))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Map<String, Object> itemMap = castMap(entry.getValue());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("instance", entry.getKey());
                    item.put("connectionType", connectionType);
                    item.put("enable", parseBoolean(itemMap, "Enable._value"));
                    item.put("connectionName", firstNonBlank(
                            extractString(itemMap, "Name._value"),
                            extractString(itemMap, "X_FH_ConnectionName._value")
                    ));
                    item.put("connectionMode", extractString(itemMap, "ConnectionType._value"));
                    item.put("serviceList", firstNonBlank(
                            extractString(itemMap, "X_FH_ServiceList._value"),
                            extractString(itemMap, "X_HW_SERVICELIST._value")
                    ));
                    item.put("ipAddress", extractString(itemMap, "ExternalIPAddress._value"));
                    item.put("username", extractString(itemMap, "Username._value"));
                    item.put("password", extractString(itemMap, "Password._value"));
                    item.put("nat", parseBoolean(itemMap, "NATEnabled._value"));
                    item.put("vlanEnabled", parseBoolean(itemMap, "X_FH_VLANEnabled._value"));
                    item.put("vlanId", firstNonBlank(
                            extractString(itemMap, "X_FH_VLANIDMark._value"),
                            extractString(itemMap, "X_HW_VLAN._value")
                    ));
                    item.put("lanBind", firstNonBlank(
                            extractString(itemMap, "X_FH_LanInterface._value"),
                            extractString(itemMap, "X_HW_LanInterface._value")
                    ));
                    item.put("ipMode", extractString(itemMap, "AddressingType._value"));
                    item.put("gateway", extractString(itemMap, "DefaultGateway._value"));
                    item.put("subnetMask", extractString(itemMap, "SubnetMask._value"));
                    item.put("dnsServers", extractString(itemMap, "DNSServers._value"));
                    items.add(item);
                });
        return items;
    }

    private List<Map<String, Object>> buildSsidList(Map<String, Object> remoteDevice) {
        List<Map<String, Object>> ssids = new ArrayList<>();
        Map<String, Object> wlanMap = extractMap(remoteDevice, "InternetGatewayDevice.LANDevice.1.WLANConfiguration");
        if (wlanMap != null) {
            wlanMap.entrySet().stream()
                    .filter(entry -> entry.getValue() instanceof Map<?, ?> && entry.getKey().matches("\\d+"))
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        Map<String, Object> wlan = castMap(entry.getValue());
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("instance", entry.getKey());
                        item.put("enable", parseBoolean(wlan, "Enable._value"));
                        item.put("ssid", extractString(wlan, "SSID._value"));
                        item.put("security", resolveSecurityLabel(wlan));
                        item.put("password", firstNonBlank(
                                extractString(wlan, "PreSharedKey.1.KeyPassphrase._value"),
                                extractString(wlan, "KeyPassphrase._value")
                        ));
                        item.put("powerPercent", firstNonBlank(
                                extractString(wlan, "X_FH_TxPowerPercent._value"),
                                extractString(wlan, "X_CT-COM_PowerLevel._value")
                        ));
                        item.put("autoChannel", parseBoolean(wlan, "AutoChannelEnable._value"));
                        item.put("channel", extractString(wlan, "Channel._value"));
                        item.put("connected", countAssociatedClients(wlan));
                        ssids.add(item);
                    });
        }
        return ssids;
    }

    private List<Map<String, Object>> buildWifiClients(Map<String, Object> remoteDevice) {
        List<Map<String, Object>> clients = new ArrayList<>();
        Map<String, Object> associatedDeviceMap = extractMap(remoteDevice,
                "InternetGatewayDevice.LANDevice.1.WLANConfiguration.1.AssociatedDevice");
        if (associatedDeviceMap == null) {
            return clients;
        }

        associatedDeviceMap.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof Map<?, ?> && entry.getKey().matches("\\d+"))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Map<String, Object> itemMap = castMap(entry.getValue());
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("hostName", firstNonBlank(
                            extractString(itemMap, "HostName._value"),
                            extractString(itemMap, "X_FH_HostName._value")
                    ));
                    item.put("ipAddress", firstNonBlank(
                            extractString(itemMap, "AssociatedDeviceIPAddress._value"),
                            extractString(itemMap, "IPAddress._value")
                    ));
                    item.put("macAddress", firstNonBlank(
                            extractString(itemMap, "AssociatedDeviceMACAddress._value"),
                            extractString(itemMap, "MACAddress._value")
                    ));
                    item.put("widthFreq", firstNonBlank(
                            extractString(itemMap, "OperatingStandard._value"),
                            extractString(itemMap, "X_FH_WidthFreq._value")
                    ));
                    item.put("rssi", firstNonBlank(
                            extractString(itemMap, "SignalStrength._value"),
                            extractString(itemMap, "X_FH_RSSI._value")
                    ));
                    item.put("noise", firstNonBlank(
                            extractString(itemMap, "Noise._value"),
                            extractString(itemMap, "X_FH_Noise._value")
                    ));
                    clients.add(item);
                });
        return clients;
    }

    private String resolveSecurityLabel(Map<String, Object> wlan) {
        String mode = firstNonBlank(
                extractString(wlan, "BeaconType._value"),
                extractString(wlan, "WPAAuthenticationMode._value"),
                extractString(wlan, "IEEE11iAuthenticationMode._value")
        );
        if (!StringUtils.hasText(mode)) {
            return "Open";
        }
        String normalized = mode.toUpperCase(Locale.ROOT);
        if (normalized.contains("11I") || normalized.contains("WPA2")) {
            return "WPA2-PSK";
        }
        if (normalized.contains("WPA")) {
            return "WPA-PSK";
        }
        return mode;
    }

    private int countAssociatedClients(Map<String, Object> wlan) {
        Map<String, Object> clients = extractMap(wlan, "AssociatedDevice");
        if (clients == null) {
            return 0;
        }
        return (int) clients.entrySet().stream()
                .filter(entry -> entry.getKey().matches("\\d+") && entry.getValue() instanceof Map<?, ?>)
                .count();
    }

    private int resolveNextInstance(Map<String, Object> remoteDevice, String objectPrefix) {
        Map<String, Object> objectMap = extractMap(remoteDevice, objectPrefix);
        if (objectMap == null) {
            return 1;
        }
        return objectMap.keySet().stream()
                .filter(key -> key.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0) + 1;
    }

    private void addParam(List<List<Object>> parameterValues, String path, Object value, String type) {
        if (value == null) {
            return;
        }
        if (value instanceof String stringValue && !StringUtils.hasText(stringValue)) {
            return;
        }
        parameterValues.add(List.of(path, value, type));
    }

    private void applySecurityParams(List<List<Object>> parameterValues, String wlanPath, String security, Object password) {
        if (!StringUtils.hasText(security)) {
            return;
        }
        String normalized = security.trim().toUpperCase(Locale.ROOT);
        if ("OPEN".equals(normalized)) {
            parameterValues.add(List.of(wlanPath + "BeaconType", "None", "xsd:string"));
            parameterValues.add(List.of(wlanPath + "WPAAuthenticationMode", "None", "xsd:string"));
            parameterValues.add(List.of(wlanPath + "IEEE11iAuthenticationMode", "None", "xsd:string"));
            return;
        }

        if (normalized.contains("WPA2")) {
            parameterValues.add(List.of(wlanPath + "BeaconType", "11i", "xsd:string"));
            parameterValues.add(List.of(wlanPath + "IEEE11iAuthenticationMode", "PSKAuthentication", "xsd:string"));
        } else {
            parameterValues.add(List.of(wlanPath + "BeaconType", "WPA", "xsd:string"));
            parameterValues.add(List.of(wlanPath + "WPAAuthenticationMode", "PSKAuthentication", "xsd:string"));
        }

        addParam(parameterValues, wlanPath + "PreSharedKey.1.KeyPassphrase", password, "xsd:string");
    }

    private String resolveStatus(Map<String, Object> remoteDevice) {
        Instant lastInform = extractInstant(remoteDevice, "_lastInform");
        if (lastInform == null) {
            return "offline";
        }
        return lastInform.isAfter(Instant.now().minusSeconds(10 * 60L)) ? "online" : "offline";
    }

    private String normalizeBaseUrl(String serverUrl) {
        String value = trimToNull(serverUrl);
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private synchronized void ensureAcsSchema() {
        forceCreateAcsSchema();
    }

    private void applySchema(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("ACS schema ensure skipped for SQL [{}]: {}", sql, ex.getMessage());
        }
    }

    private void forceCreateAcsSchema() {
        applySchema("""
                CREATE TABLE IF NOT EXISTS acs_devices (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    acs_device_id VARCHAR(255),
                    ip_address VARCHAR(15),
                    mac_address VARCHAR(17),
                    device_type VARCHAR(50),
                    vendor VARCHAR(50),
                    model VARCHAR(50),
                    serial_number VARCHAR(50),
                    firmware_version VARCHAR(50),
                    software_version VARCHAR(50),
                    olt_id BIGINT,
                    olt_port INTEGER,
                    onu_id INTEGER,
                    optical_rx_power DOUBLE PRECISION,
                    optical_tx_power DOUBLE PRECISION,
                    optical_temperature DOUBLE PRECISION,
                    optical_voltage DOUBLE PRECISION,
                    wifi_name VARCHAR(100),
                    status VARCHAR(20) DEFAULT 'offline',
                    connection_type VARCHAR(20),
                    distance INTEGER,
                    last_acs_request TIMESTAMP,
                    last_inform TIMESTAMP,
                    is_provisioned BOOLEAN DEFAULT FALSE,
                    is_active BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_synced_at TIMESTAMP
                )
                """);
        applySchema("ALTER TABLE IF EXISTS acs_devices ADD COLUMN IF NOT EXISTS acs_device_id VARCHAR(255)");
        applySchema("ALTER TABLE IF EXISTS acs_devices ADD COLUMN IF NOT EXISTS wifi_name VARCHAR(100)");
        applySchema("ALTER TABLE IF EXISTS acs_devices ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP");
        applySchema("CREATE UNIQUE INDEX IF NOT EXISTS idx_acs_devices_acs_device_id ON acs_devices(acs_device_id)");
        applySchema("""
                CREATE TABLE IF NOT EXISTS acs_settings (
                    id BIGINT PRIMARY KEY,
                    server_url VARCHAR(255),
                    username VARCHAR(120),
                    password VARCHAR(255),
                    is_active BOOLEAN DEFAULT TRUE,
                    last_connection_status VARCHAR(30),
                    last_connection_message TEXT,
                    last_connected_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        try {
            Integer existing = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM acs_settings WHERE id = ?",
                    Integer.class,
                    AcsSettings.SINGLETON_ID
            );
            if (existing == null || existing == 0) {
                jdbcTemplate.update(
                        "INSERT INTO acs_settings (id, is_active, created_at, updated_at) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        AcsSettings.SINGLETON_ID,
                        true
                );
            }
        } catch (Exception ex) {
            log.warn("ACS default settings bootstrap skipped: {}", ex.getMessage());
        }
    }

    private boolean isMissingAcsTable(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if ((normalized.contains("acs_settings") || normalized.contains("acs_devices"))
                        && (normalized.contains("does not exist")
                        || normalized.contains("not found")
                        || normalized.contains("invalid object name")
                        || normalized.contains("no such table"))) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private RuntimeException mapRestClientException(RestClientResponseException ex, String configuredUrl) {
        if (ex.getStatusCode().value() == 404) {
            return new IllegalStateException(
                    "Endpoint GenieACS tidak ditemukan pada " + configuredUrl
                            + ". Pastikan Anda memakai base API yang benar. Server Anda bisa saja memakai `/devices` langsung atau `/nbi/devices`."
            );
        }
        if (ex.getStatusCode().value() == 405) {
            return new IllegalStateException(
                    "Endpoint GenieACS menolak method pada " + configuredUrl
                            + ". Periksa base URL NBI (biasanya port 7557) dan path `/nbi` jika dibutuhkan."
            );
        }
        if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
            return new IllegalStateException("Autentikasi GenieACS gagal. Periksa username/password NBI.");
        }
        return new IllegalStateException("Gagal menghubungi GenieACS: HTTP " + ex.getStatusCode().value() + " " + ex.getStatusText());
    }

    private <T> T requestWithFallback(AcsSettings settings, String path, Function<String, T> executor) {
        RestClientResponseException lastException = null;
        for (String baseUrl : candidateBaseUrls(settings.getServerUrl())) {
            String targetUrl = baseUrl + path;
            try {
                return executor.apply(targetUrl);
            } catch (RestClientResponseException ex) {
                lastException = ex;
                int status = ex.getStatusCode().value();
                if (status != 404 && status != 405) {
                    throw mapRestClientException(ex, targetUrl);
                }
            }
        }
        if (lastException != null) {
            throw mapRestClientException(lastException, settings.getServerUrl());
        }
        throw new IllegalStateException("Tidak ada endpoint GenieACS yang bisa dicoba");
    }

    private List<String> candidateBaseUrls(String configuredUrl) {
        String normalized = normalizeBaseUrl(configuredUrl);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.endsWith("/nbi")) {
            return List.of(normalized, normalized.substring(0, normalized.length() - 4));
        }
        return List.of(normalized + "/nbi", normalized);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Double firstNonNullDouble(Double... values) {
        for (Double value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Long firstNonNullLong(Long... values) {
        for (Long value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String extractString(Map<String, Object> source, String path) {
        Object value = extractValue(source, path);
        return value != null ? String.valueOf(value) : null;
    }

    private Double extractDouble(Map<String, Object> source, String path) {
        Object value = extractValue(source, path);
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long extractLong(Map<String, Object> source, String path) {
        Object value = extractValue(source, path);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private LocalDateTime extractDateTime(Map<String, Object> source, String path) {
        Instant instant = extractInstant(source, path);
        if (instant != null) {
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        }
        return null;
    }

    private Instant extractInstant(Map<String, Object> source, String path) {
        Object value = extractValue(source, path);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Object extractValue(Map<String, Object> source, String path) {
        if (source == null || !StringUtils.hasText(path)) {
            return null;
        }
        Object current = source;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Map<String, Object> extractMap(Map<String, Object> source, String path) {
        Object value = extractValue(source, path);
        if (value instanceof Map<?, ?> map) {
            return castMap(map);
        }
        return null;
    }

    private Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private Boolean parseBoolean(Map<String, Object> source, String path) {
        String value = extractString(source, path);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "enabled".equalsIgnoreCase(value);
    }

    private String formatDuration(Long seconds) {
        if (seconds == null || seconds < 0) {
            return null;
        }
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainSeconds = seconds % 60;
        if (days > 0) {
            return "%d Days, %02d:%02d:%02d".formatted(days, hours, minutes, remainSeconds);
        }
        return "%02d:%02d:%02d".formatted(hours, minutes, remainSeconds);
    }
}
