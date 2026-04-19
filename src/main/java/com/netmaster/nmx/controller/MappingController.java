package com.netmaster.nmx.controller;

import com.netmaster.nmx.config.TenantContextHolder;
import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.model.Customer;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.model.NetworkTopologyLink;
import com.netmaster.nmx.model.Odc;
import com.netmaster.nmx.model.Odp;
import com.netmaster.nmx.model.OntActionLog;
import com.netmaster.nmx.model.Server;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import com.netmaster.nmx.repository.NetworkTopologyLinkRepository;
import com.netmaster.nmx.repository.OdcRepository;
import com.netmaster.nmx.repository.OdpRepository;
import com.netmaster.nmx.repository.OntActionLogRepository;
import com.netmaster.nmx.repository.ServerRepository;
import com.netmaster.nmx.security.TenantRoleAccess;
import lombok.extern.slf4j.Slf4j;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netmaster.nmx.service.GenieAcsService;

@RestController
@RequestMapping("/api/mapping")
@RequiredArgsConstructor
@Slf4j
public class MappingController {

    private static final Pattern COORDINATE_PATTERN = Pattern.compile("-?\\\\d+(?:\\\\.\\\\d+)?");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final OdcRepository odcRepository;
    private final OdpRepository odpRepository;
    private final ServerRepository serverRepository;
    private final CustomerServiceEntityRepository customerServiceEntityRepository;
    private final NetworkTopologyLinkRepository networkTopologyLinkRepository;
    private final OntActionLogRepository ontActionLogRepository;
    private final GenieAcsService genieAcsService;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, Boolean> mappingSchemaReadyByTenant = new ConcurrentHashMap<>();
    private final Map<String, Object> mappingSchemaLocks = new ConcurrentHashMap<>();

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<MappingOverviewResponse>> getOverview() {
        try {
            ensureMappingSchema();
        } catch (Exception ex) {
            return schemaUnavailableResponse(ex);
        }
        if (!hasPermission("READ")) {
            return forbidden("Akses ditolak");
        }

        List<MappingServerDto> servers = serverRepository.findByIsActiveTrueOrderByNameAsc().stream()
                .map(this::toServerDto)
                .toList();

        List<MappingFiberNodeDto> fiberNodes = new ArrayList<>();
        odcRepository.findAllActiveWithServer().stream()
                .map(this::toOdcNodeDto)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(fiberNodes::add);
        odpRepository.findAllActiveWithOdcAndServer().stream()
                .map(this::toOdpNodeDto)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(fiberNodes::add);

        List<MappingOntMarkerDto> onts = customerServiceEntityRepository.findAllForMapping().stream()
                .map(this::toOntMarkerDto)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        List<MappingLinkDto> links = networkTopologyLinkRepository.findByIsActiveTrueOrderByIdAsc().stream()
                .map(this::toLinkDto)
                .toList();

        MappingOverviewResponse response = new MappingOverviewResponse(
                servers,
                fiberNodes,
                onts,
                links,
                hasPermission("WRITE")
        );
        return ResponseEntity.ok(ApiResponse.success("Data mapping berhasil diambil", response));
    }

    @GetMapping("/onts/{serviceId}")
    public ResponseEntity<ApiResponse<MappingOntDetailDto>> getOntDetail(@PathVariable Long serviceId) {
        try {
            ensureMappingSchema();
        } catch (Exception ex) {
            return schemaUnavailableResponse(ex);
        }
        if (!hasPermission("READ")) {
            return forbidden("Akses ditolak");
        }

        return customerServiceEntityRepository.findMappingDetailById(serviceId)
                .map(service -> ResponseEntity.ok(ApiResponse.success("Detail ONT berhasil diambil", toOntDetailDto(service))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Data ONT tidak ditemukan")));
    }

    @GetMapping("/nodes/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<MappingFiberNodeDto>> getNode(@PathVariable Long id) {
        try {
            ensureMappingSchema();
        } catch (Exception ex) {
            return schemaUnavailableResponse(ex);
        }
        if (!hasPermission("READ")) {
            return forbidden("Akses ditolak");
        }

        Optional<Odc> odc = odcRepository.findById(id);
        if (odc.isPresent()) {
            return toOdcNodeDto(odc.get())
                    .map(node -> ResponseEntity.ok(ApiResponse.success("Data ODC ditemukan", node)))
                    .orElse(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Gagal memproses data ODC")));
        }

        Optional<Odp> odp = odpRepository.findById(id);
        if (odp.isPresent()) {
            return toOdpNodeDto(odp.get())
                    .map(node -> ResponseEntity.ok(ApiResponse.success("Data ODP ditemukan", node)))
                    .orElse(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Gagal memproses data ODP")));
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Node ODP/ODC tidak ditemukan"));
    }

    @PostMapping("/nodes")
    @Transactional
    public ResponseEntity<ApiResponse<MappingFiberNodeDto>> createNode(@RequestBody MappingNodeCreateRequest request) {
        try {
            ensureMappingSchema();
        } catch (Exception ex) {
            return schemaUnavailableResponse(ex);
        }
        if (!hasPermission("WRITE")) {
            return forbidden("Akses ditolak - hanya Admin dan Super Admin yang dapat menambah ODP/ODC");
        }

        try {
            String nodeType = normalizeCreateNodeType(request.nodeType());
            String name = trimToNull(request.name());
            if (!StringUtils.hasText(name)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Nama ODP/ODC wajib diisi"));
            }
            Long serverId = request.companyProfileId();
            if (serverId == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Server wajib dipilih"));
            }
            if (request.latitude() == null || request.longitude() == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Koordinat wajib dipilih dari peta"));
            }

            Server server = serverRepository.findById(serverId).orElse(null);
            if (server == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Server tidak ditemukan"));
            }

            String location = buildNodeLocation(server, request.latitude(), request.longitude());

            if ("ODC".equals(nodeType)) {
                Odc odc = new Odc();
                odc.setName(name);
                odc.setLocation(location);
                odc.setLatitude(request.latitude());
                odc.setLongitude(request.longitude());
                odc.setCapacity(32);
                odc.setUsedPort(0);
                odc.setIsActive(true);
                odc.setServer(server);
                Odc saved = odcRepository.save(odc);
                return toOdcNodeDto(saved)
                        .map(node -> ResponseEntity.ok(ApiResponse.success("Data ODC berhasil disimpan", node)))
                        .orElse(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error("Data ODC berhasil disimpan tetapi gagal dibaca ulang")));
            }

            Odp odp = new Odp();
            odp.setNodeType(nodeType);
            odp.setName(name);
            odp.setSplitter(trimToNull(request.splitter()));
            odp.setLatitude(request.latitude());
            odp.setLongitude(request.longitude());
            odp.setLocation(location);
            odp.setCapacity(8);
            odp.setUsedPort(0);
            odp.setIsActive(true);
            resolveSingleActiveOdcForMapping().ifPresent(odp::setOdc);

            Odp saved = odpRepository.save(odp);
            return toOdpNodeDto(saved)
                    .map(node -> ResponseEntity.ok(ApiResponse.success("Data ODP berhasil disimpan", node)))
                    .orElse(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ApiResponse.error("Data ODP berhasil disimpan tetapi gagal dibaca ulang")));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/links")
    @Transactional
    public ResponseEntity<ApiResponse<MappingLinkDto>> createLink(@RequestBody MappingLinkRequest request) {
        try {
            ensureMappingSchema();
        } catch (Exception ex) {
            return schemaUnavailableResponse(ex);
        }
        if (!hasPermission("WRITE")) {
            return forbidden("Akses ditolak - hanya Admin dan Super Admin yang dapat menyimpan garis");
        }

        try {
            NormalizedLink normalizedLink = normalizeLink(request);
            if (!nodeExists(normalizedLink.fromType(), normalizedLink.fromId())
                    || !nodeExists(normalizedLink.toType(), normalizedLink.toId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ApiResponse.error("Node topologi tidak valid atau belum ada di database"));
            }

            Optional<NetworkTopologyLink> existing = networkTopologyLinkRepository
                    .findByIsActiveTrueAndFromNodeTypeAndFromNodeIdAndToNodeTypeAndToNodeId(
                            normalizedLink.fromType(),
                            normalizedLink.fromId(),
                            normalizedLink.toType(),
                            normalizedLink.toId()
                    );
            if (existing.isPresent()) {
                return ResponseEntity.ok(ApiResponse.success("Garis topologi sudah tersedia", toLinkDto(existing.get())));
            }

            NetworkTopologyLink link = new NetworkTopologyLink();
            link.setFromNodeType(normalizedLink.fromType());
            link.setFromNodeId(normalizedLink.fromId());
            link.setToNodeType(normalizedLink.toType());
            link.setToNodeId(normalizedLink.toId());
            link.setLineColor(resolveLineColor(normalizedLink.fromType(), normalizedLink.toType()));

            NetworkTopologyLink saved = networkTopologyLinkRepository.save(link);
            return ResponseEntity.ok(ApiResponse.success("Garis topologi berhasil disimpan", toLinkDto(saved)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @DeleteMapping("/links/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteLink(@PathVariable Long id) {
        try {
            ensureMappingSchema();
        } catch (Exception ex) {
            return schemaUnavailableResponse(ex);
        }
        if (!hasPermission("WRITE")) {
            return forbidden("Akses ditolak - hanya Admin dan Super Admin yang dapat menghapus garis");
        }

        return networkTopologyLinkRepository.findById(id)
                .map(link -> {
                    link.setIsActive(false);
                    networkTopologyLinkRepository.save(link);
                    return ResponseEntity.ok(ApiResponse.<Void>success("Garis topologi berhasil dihapus", null));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.<Void>error("Garis topologi tidak ditemukan")));
    }

    @PostMapping("/onts/{serviceId}/restart")
    @Transactional
    public ResponseEntity<ApiResponse<MappingOntDetailDto>> restartOnt(@PathVariable Long serviceId) {
        try {
            ensureMappingSchema();
        } catch (Exception ex) {
            return schemaUnavailableResponse(ex);
        }
        if (!hasPermission("WRITE")) {
            return forbidden("Akses ditolak - hanya Admin dan Super Admin yang dapat menjalankan aksi ONT");
        }

        CustomerServiceEntity service = customerServiceEntityRepository.findMappingDetailById(serviceId)
                .orElse(null);
        if (service == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Data ONT tidak ditemukan"));
        }

        LocalDateTime requestTime = LocalDateTime.now();
        service.setLastRestartRequestedAt(requestTime);
        customerServiceEntityRepository.save(service);

        String actionStatus = "queued";
        String message = "Permintaan restart ONT berhasil disimpan";
        try {
            if (StringUtils.hasText(service.getOntSerial()) && genieAcsService.isConfigured()) {
                genieAcsService.submitRebootBySerial(service.getOntSerial());
                actionStatus = "submitted_to_acs";
                message = "Perintah restart berhasil dikirim ke GenieACS";
            }
        } catch (Exception ex) {
            actionStatus = "acs_failed";
            message = "Permintaan restart disimpan, tetapi gagal dikirim ke GenieACS: " + ex.getMessage();
        }

        saveOntAction(serviceId, "RESTART_ONT", "{\"requestedAt\":\"" + requestTime + "\"}", actionStatus);

        return ResponseEntity.ok(ApiResponse.success(
                message,
                toOntDetailDto(service)
        ));
    }

    @PutMapping("/onts/{serviceId}/wifi")
    @Transactional
    public ResponseEntity<ApiResponse<MappingOntDetailDto>> updateWifi(
            @PathVariable Long serviceId,
            @RequestBody MappingWifiUpdateRequest request
    ) {
        try {
            ensureMappingSchema();
        } catch (Exception ex) {
            return schemaUnavailableResponse(ex);
        }
        if (!hasPermission("WRITE")) {
            return forbidden("Akses ditolak - hanya Admin dan Super Admin yang dapat mengubah WiFi ONT");
        }

        String wifiName = trimToNull(request.wifiName());
        String wifiPassword = trimToNull(request.wifiPassword());
        if (!StringUtils.hasText(wifiName)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Nama WiFi wajib diisi"));
        }
        if (!StringUtils.hasText(wifiPassword)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Password WiFi wajib diisi"));
        }

        CustomerServiceEntity service = customerServiceEntityRepository.findMappingDetailById(serviceId)
                .orElse(null);
        if (service == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Data ONT tidak ditemukan"));
        }

        service.setWifiName(wifiName);
        service.setWifiPassword(wifiPassword);
        CustomerServiceEntity saved = customerServiceEntityRepository.save(service);

        String actionStatus = "queued";
        String message = "Nama WiFi dan password berhasil diperbarui";
        try {
            if (StringUtils.hasText(saved.getOntSerial()) && genieAcsService.isConfigured()) {
                genieAcsService.submitWifiUpdateBySerial(saved.getOntSerial(), wifiName, wifiPassword);
                actionStatus = "submitted_to_acs";
                message = "Nama WiFi dan password berhasil diperbarui dan dikirim ke GenieACS";
            }
        } catch (Exception ex) {
            actionStatus = "acs_failed";
            message = "WiFi lokal diperbarui, tetapi gagal dikirim ke GenieACS: " + ex.getMessage();
        }

        saveOntAction(
                serviceId,
                "UPDATE_WIFI",
                "{\"wifiName\":\"" + escapeJson(wifiName) + "\",\"wifiPassword\":\"" + escapeJson(wifiPassword) + "\"}",
                actionStatus
        );

        return ResponseEntity.ok(ApiResponse.success(
                message,
                toOntDetailDto(saved)
        ));
    }

    @PutMapping("/nodes/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<MappingFiberNodeDto>> updateNode(@PathVariable Long id, @RequestBody MappingNodeUpdateRequest request) {
        try {
            ensureMappingSchema();
        } catch (Exception ex) {
            return schemaUnavailableResponse(ex);
        }
        if (!hasPermission("WRITE")) {
            return forbidden("Akses ditolak - hanya Admin dapat mengedit");
        }

        String name = trimToNull(request.name());
        if (!StringUtils.hasText(name)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Nama wajib diisi"));
        }
        Long serverId = request.companyProfileId();
        if (serverId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Server wajib dipilih"));
        }
        if (request.latitude() == null || request.longitude() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Koordinat wajib diisi"));
        }

        Server server = serverRepository.findById(serverId).orElse(null);
        if (server == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Server tidak ditemukan"));
        }

        Optional<Odc> odcOpt = odcRepository.findById(id);
        if (odcOpt.isPresent()) {
            String location = buildNodeLocation(server, request.latitude(), request.longitude());
            int updated = odcRepository.updateMappingNode(
                    id,
                    name,
                    request.latitude(),
                    request.longitude(),
                    location,
                    server
            );
            if (updated == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Node tidak ditemukan"));
            }
            Odc refreshed = odcRepository.findById(id).orElse(null);
            return toOdcNodeDto(refreshed)
                    .map(dto -> ResponseEntity.ok(ApiResponse.success("ODC berhasil diupdate", dto)))
                    .orElse(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Update sukses tapi gagal baca ulang")));
        }

        Optional<Odp> odpOpt = odpRepository.findById(id);
        if (odpOpt.isPresent()) {
            String location = buildNodeLocation(server, request.latitude(), request.longitude());
            int updated = odpRepository.updateMappingNode(
                    id,
                    name,
                    trimToNull(request.splitter()),
                    request.latitude(),
                    request.longitude(),
                    location
            );
            if (updated == 0) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Node tidak ditemukan"));
            }
            Odp refreshed = odpRepository.findById(id).orElse(null);
            return toOdpNodeDto(refreshed)
                    .map(dto -> ResponseEntity.ok(ApiResponse.success("ODP berhasil diupdate", dto)))
                    .orElse(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error("Update sukses tapi gagal baca ulang")));
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Node tidak ditemukan"));
    }

    @DeleteMapping("/nodes/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteNode(@PathVariable Long id) {
        try {
            ensureMappingSchema();
        } catch (Exception ex) {
            return schemaUnavailableResponse(ex);
        }
        if (!hasPermission("WRITE")) {
            return forbidden("Akses ditolak - hanya Admin dapat menghapus");
        }

        Optional<Odc> odc = odcRepository.findById(id);
        if (odc.isPresent()) {
            List<Long> odpIds = odpRepository.findIdsByOdcId(id);
            if (!odpIds.isEmpty()) {
                customerServiceEntityRepository.deleteByOdpIds(odpIds);
                networkTopologyLinkRepository.deleteLinksForNodes("ODP", odpIds);
                odpRepository.deleteByOdcId(id);
            }
            networkTopologyLinkRepository.deleteLinksForNode("ODC", id);
            odcRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.success("ODC berhasil dihapus beserta ODP terkait.", null));
        }

        Optional<Odp> odp = odpRepository.findById(id);
        if (odp.isPresent()) {
            long ontCount = customerServiceEntityRepository.countByOdpId(id);
            if (ontCount > 0) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error("ODP masih dipakai oleh ONT. Pindahkan ONT terlebih dahulu."));
            }
            networkTopologyLinkRepository.deleteLinksForNode("ODP", id);
            odpRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.success("ODP berhasil dihapus.", null));
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Node tidak ditemukan"));
    }

    private Optional<MappingFiberNodeDto> toOdcNodeDto(Odc odc) {
        if (odc == null || odc.getId() == null || odc.getLatitude() == null || odc.getLongitude() == null) {
            return Optional.empty();
        }
        return Optional.of(new MappingFiberNodeDto(
                "ODC",
                odc.getId(),
                safeText(odc.getCode()),
                safeText(odc.getName()),
                null,
                odc.getLatitude(),
                odc.getLongitude(),
                safeText(odc.getLocation()),
                odc.getServer() != null ? safeText(odc.getServer().getName()) : null,
                null
        ));
    }

    private Optional<MappingFiberNodeDto> toOdpNodeDto(Odp odp) {
        if (odp == null || odp.getId() == null || odp.getLatitude() == null || odp.getLongitude() == null) {
            return Optional.empty();
        }
        String nodeType = valueOrFallback(safeText(odp.getNodeType()), "ODP");
        String serverName = odp.getOdc() != null && odp.getOdc().getServer() != null ? safeText(odp.getOdc().getServer().getName()) : null;
        String parentName = odp.getOdc() != null ? safeText(odp.getOdc().getName()) : null;
        return Optional.of(new MappingFiberNodeDto(
                nodeType,
                odp.getId(),
                safeText(odp.getCode()),
                safeText(odp.getName()),
                safeText(odp.getSplitter()),
                odp.getLatitude(),
                odp.getLongitude(),
                safeText(odp.getLocation()),
                serverName,
                parentName
        ));
    }

    private Optional<MappingOntMarkerDto> toOntMarkerDto(CustomerServiceEntity service) {
        if (service == null || service.getId() == null || service.getCustomer() == null) {
            return Optional.empty();
        }

        Customer customer = service.getCustomer();
        if (customer.getLatitude() == null || customer.getLongitude() == null) {
            return Optional.empty();
        }

        String odpName = service.getOdp() != null ? safeText(service.getOdp().getName()) : null;
        String odcName = service.getOdp() != null && service.getOdp().getOdc() != null
                ? safeText(service.getOdp().getOdc().getName())
                : null;
        String serverName = service.getOdp() != null && service.getOdp().getOdc() != null && service.getOdp().getOdc().getServer() != null
                ? safeText(service.getOdp().getOdc().getServer().getName())
                : null;

        return Optional.of(new MappingOntMarkerDto(
                service.getId(),
                customer.getId(),
                safeText(customer.getCustomerCode()),
                safeText(customer.getFullName()),
                customer.getLatitude(),
                customer.getLongitude(),
                safeText(customer.getInstallationAddress()),
                safeText(service.getStatus()),
                odpName,
                odcName,
                serverName
        ));
    }

    private MappingServerDto toServerDto(Server server) {
        return new MappingServerDto(
                server.getId(),
                safeText(server.getName()),
                safeText(server.getLocation()),
                null,
                server.getLatitude(),
                server.getLongitude(),
                server.getLatitude() != null && server.getLongitude() != null
        );
    }

    private MappingOntDetailDto toOntDetailDto(CustomerServiceEntity service) {
        Customer customer = service.getCustomer();
        String odpName = service.getOdp() != null ? safeText(service.getOdp().getName()) : null;
        String odcName = service.getOdp() != null && service.getOdp().getOdc() != null
                ? safeText(service.getOdp().getOdc().getName())
                : null;
        String serverName = service.getOdp() != null && service.getOdp().getOdc() != null && service.getOdp().getOdc().getServer() != null
                ? safeText(service.getOdp().getOdc().getServer().getName())
                : null;

        String ontRedaman = service.getOntRedaman() != null ? service.getOntRedaman().stripTrailingZeros().toPlainString() + " dBm" : "Belum ada data";
        String standbyDuration = formatStandbyDuration(service.getOntStandbySince());
        String lastRestartRequestedAt = service.getLastRestartRequestedAt() != null
                ? service.getLastRestartRequestedAt().format(DATE_TIME_FORMATTER)
                : "Belum pernah";

        return new MappingOntDetailDto(
                service.getId(),
                customer != null ? customer.getId() : null,
                customer != null ? safeText(customer.getCustomerCode()) : null,
                customer != null ? safeText(customer.getFullName()) : null,
                safeText(service.getStatus()),
                ontRedaman,
                valueOrFallback(service.getWifiName(), "Belum diatur"),
                valueOrFallback(service.getWifiPassword(), "Belum diatur"),
                standbyDuration,
                valueOrFallback(service.getOntSerial(), "-"),
                valueOrFallback(service.getOntBrand(), "-"),
                valueOrFallback(service.getPppoeUsername(), "-"),
                valueOrFallback(service.getIpAddress(), "-"),
                valueOrFallback(odpName, "-"),
                valueOrFallback(odcName, "-"),
                valueOrFallback(serverName, "-"),
                lastRestartRequestedAt
        );
    }

    private MappingLinkDto toLinkDto(NetworkTopologyLink link) {
        return new MappingLinkDto(
                link.getId(),
                link.getFromNodeType(),
                link.getFromNodeId(),
                link.getToNodeType(),
                link.getToNodeId(),
                valueOrFallback(link.getLineColor(), "#fb923c")
        );
    }

    private Optional<CoordinateValue> parseCoordinates(String rawCoordinates) {
        if (!StringUtils.hasText(rawCoordinates)) {
            return Optional.empty();
        }

        Matcher matcher = COORDINATE_PATTERN.matcher(rawCoordinates);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group());
            if (values.size() == 2) {
                break;
            }
        }

        if (values.size() < 2) {
            return Optional.empty();
        }

        try {
            return Optional.of(new CoordinateValue(new BigDecimal(values.get(0)), new BigDecimal(values.get(1))));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String formatStandbyDuration(LocalDateTime standbySince) {
        if (standbySince == null) {
            return "Belum ada data";
        }

        Duration duration = Duration.between(standbySince, LocalDateTime.now());
        if (duration.isNegative()) {
            return "0 menit";
        }

        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();

        List<String> parts = new ArrayList<>();
        if (days > 0) {
            parts.add(days + " hari");
        }
        if (hours > 0) {
            parts.add(hours + " jam");
        }
        if (minutes > 0 || parts.isEmpty()) {
            parts.add(minutes + " menit");
        }
        return String.join(" ", parts);
    }

    private void saveOntAction(Long serviceId, String actionType, String payload, String status) {
        OntActionLog actionLog = new OntActionLog();
        actionLog.setCustomerServiceId(serviceId);
        actionLog.setActionType(actionType);
        actionLog.setPayload(payload);
        actionLog.setRequestedBy(currentUsername());
        actionLog.setStatus(status);
        ontActionLogRepository.save(actionLog);
    }

    private boolean nodeExists(String nodeType, Long nodeId) {
        return switch (nodeType) {
            case "SERVER" -> serverRepository.existsById(nodeId);
            case "ODC" -> odcRepository.existsById(nodeId) || odpRepository.existsByIdAndNodeTypeIgnoreCase(nodeId, "ODC");
            case "ODP" -> odpRepository.existsById(nodeId);
            case "ONT" -> customerServiceEntityRepository.existsById(nodeId);
            default -> false;
        };
    }

    private NormalizedLink normalizeLink(MappingLinkRequest request) {
        String fromType = normalizeNodeType(request.fromType());
        String toType = normalizeNodeType(request.toType());
        Long fromId = request.fromId();
        Long toId = request.toId();

        if (fromId == null || toId == null) {
            throw new IllegalArgumentException("Node awal dan node tujuan wajib diisi");
        }
        if (fromType.equals(toType) && fromId.equals(toId)) {
            throw new IllegalArgumentException("Tidak dapat menghubungkan node ke dirinya sendiri");
        }

        int fromRank = nodeRank(fromType);
        int toRank = nodeRank(toType);
        if (Math.abs(fromRank - toRank) != 1) {
            throw new IllegalArgumentException("Hanya hubungan Server-ODC, ODC-ODP, atau ODP-ONT yang diizinkan");
        }

        if (fromRank > toRank) {
            return new NormalizedLink(toType, toId, fromType, fromId);
        }
        return new NormalizedLink(fromType, fromId, toType, toId);
    }

    private String normalizeNodeType(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Tipe node wajib diisi");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("SERVER")
                && !normalized.equals("ODC")
                && !normalized.equals("ODP")
                && !normalized.equals("ONT")) {
            throw new IllegalArgumentException("Tipe node tidak valid");
        }
        return normalized;
    }

    private String normalizeCreateNodeType(String value) {
        if (!StringUtils.hasText(value)) {
            return "ODP";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("ODP") && !normalized.equals("ODC")) {
            throw new IllegalArgumentException("Jenis node tidak valid");
        }
        return normalized;
    }

    private int nodeRank(String nodeType) {
        return switch (nodeType) {
            case "SERVER" -> 0;
            case "ODC" -> 1;
            case "ODP" -> 2;
            case "ONT" -> 3;
            default -> throw new IllegalArgumentException("Tipe node tidak valid");
        };
    }

    private String resolveLineColor(String fromType, String toType) {
        if ("SERVER".equals(fromType) && "ODC".equals(toType)) {
            return "#f97316";
        }
        if ("ODC".equals(fromType) && "ODP".equals(toType)) {
            return "#22c55e";
        }
        if ("ODP".equals(fromType) && "ONT".equals(toType)) {
            return "#3b82f6";
        }
        return "#fb923c";
    }

    private boolean hasPermission(String permission) {
        return switch (permission) {
            case "READ" -> TenantRoleAccess.canRead(SecurityContextHolder.getContext().getAuthentication());
            case "WRITE" -> TenantRoleAccess.canWrite(SecurityContextHolder.getContext().getAuthentication());
            default -> false;
        };
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !StringUtils.hasText(auth.getName())) {
            return "system";
        }
        return auth.getName();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String safeText(String value) {
        return trimToNull(value);
    }

    private String valueOrFallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String escapeJson(String value) {
        return value.replace("\\\\", "\\\\\\\\").replace("\\\"", "\\\\\\\"");
    }

    private String buildNodeLocation(Server server, BigDecimal latitude, BigDecimal longitude) {
        String serverName = server != null ? trimToNull(server.getName()) : null;
        String coordinateText = "Lat " + latitude.toPlainString() + ", Lng " + longitude.toPlainString();
        return StringUtils.hasText(serverName) ? serverName + " - " + coordinateText : coordinateText;
    }

    private Optional<Server> resolveSingleActiveServerForMapping() {
        List<Server> servers = serverRepository.findByIsActiveTrueOrderByNameAsc();
        if (servers.size() == 1) {
            return Optional.of(servers.get(0));
        }
        return Optional.empty();
    }

    private Optional<Odc> resolveSingleActiveOdcForMapping() {
        List<Odc> odcs = odcRepository.findAllActiveWithServer();
        if (odcs.size() == 1) {
            return Optional.of(odcs.get(0));
        }
        return Optional.empty();
    }

    private <T> ResponseEntity<ApiResponse<T>> forbidden(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(message));
    }

    private <T> ResponseEntity<ApiResponse<T>> schemaUnavailableResponse(Exception ex) {
        log.warn("Mapping schema not ready for tenant [{}]: {}", resolveSchemaKey(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Schema mapping belum siap: " + ex.getMessage()));
    }

    private void ensureMappingSchema() {
        String schemaKey = resolveSchemaKey();
        if (Boolean.TRUE.equals(mappingSchemaReadyByTenant.get(schemaKey))) {
            return;
        }
        Object lock = mappingSchemaLocks.computeIfAbsent(schemaKey, ignored -> new Object());
        synchronized (lock) {
            if (Boolean.TRUE.equals(mappingSchemaReadyByTenant.get(schemaKey))) {
                return;
            }
            applySchema("""
                    ALTER TABLE IF EXISTS customer_services ADD COLUMN IF NOT EXISTS ont_redaman DECIMAL(6,2)
                    """);
            applySchema("""
                    ALTER TABLE IF EXISTS customer_services ADD COLUMN IF NOT EXISTS wifi_name VARCHAR(100)
                    """);
            applySchema("""
                    ALTER TABLE IF EXISTS customer_services ADD COLUMN IF NOT EXISTS wifi_password VARCHAR(100)
                    """);
            applySchema("""
                    ALTER TABLE IF EXISTS customer_services ADD COLUMN IF NOT EXISTS ont_standby_since TIMESTAMP
                    """);
            applySchema("""
                    ALTER TABLE IF EXISTS customer_services ADD COLUMN IF NOT EXISTS last_restart_requested_at TIMESTAMP
                    """);
            applySchema("""
                    CREATE TABLE IF NOT EXISTS network_topology_links (
                        id BIGSERIAL PRIMARY KEY,
                        from_node_type VARCHAR(20) NOT NULL,
                        from_node_id BIGINT NOT NULL,
                        to_node_type VARCHAR(20) NOT NULL,
                        to_node_id BIGINT NOT NULL,
                        line_color VARCHAR(20) DEFAULT '#fb923c',
                        is_active BOOLEAN DEFAULT TRUE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            applySchema("""
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_network_topology_links_pair
                    ON network_topology_links (from_node_type, from_node_id, to_node_type, to_node_id)
                    """);
            applySchema("""
                    CREATE TABLE IF NOT EXISTS ont_action_logs (
                        id BIGSERIAL PRIMARY KEY,
                        customer_service_id BIGINT NOT NULL,
                        action_type VARCHAR(40) NOT NULL,
                        payload TEXT,
                        requested_by VARCHAR(100),
                        status VARCHAR(30) DEFAULT 'queued',
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            applySchema("""
                    CREATE INDEX IF NOT EXISTS idx_ont_action_logs_service
                    ON ont_action_logs (customer_service_id)
                    """);
            mappingSchemaReadyByTenant.put(schemaKey, true);
        }
    }

    private void applySchema(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            throw new IllegalStateException("Gagal menyiapkan schema mapping: " + ex.getMessage(), ex);
        }
    }

    private String resolveSchemaKey() {
        String tenantKey = TenantContextHolder.getTenantKey();
        if (tenantKey == null || tenantKey.isBlank()) {
            return "__default__";
        }
        return tenantKey;
    }
}

record MappingOverviewResponse(
        List<MappingServerDto> servers,
        List<MappingFiberNodeDto> fiberNodes,
        List<MappingOntMarkerDto> onts,
        List<MappingLinkDto> links,
        boolean canEdit
) {
}

record MappingServerDto(
        Long id,
        String name,
        String address,
        String coordinatesText,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean hasCoordinates
) {
}

record MappingFiberNodeDto(
        String type,
        Long id,
        String code,
        String name,
        String splitter,
        BigDecimal latitude,
        BigDecimal longitude,
        String location,
        String serverName,
        String parentName
) {
}

record MappingOntMarkerDto(
        Long serviceId,
        Long customerId,
        String customerCode,
        String customerName,
        BigDecimal latitude,
        BigDecimal longitude,
        String installationAddress,
        String status,
        String odpName,
        String odcName,
        String serverName
) {
}

record MappingOntDetailDto(
        Long serviceId,
        Long customerId,
        String customerCode,
        String customerName,
        String status,
        String ontRedaman,
        String wifiName,
        String wifiPassword,
        String ontStandbyDuration,
        String ontSerial,
        String ontBrand,
        String pppoeUsername,
        String ipAddress,
        String odpName,
        String odcName,
        String serverName,
        String lastRestartRequestedAt
) {
}

record MappingLinkDto(
        Long id,
        String fromType,
        Long fromId,
        String toType,
        Long toId,
        String lineColor
) {
}

record MappingLinkRequest(
        String fromType,
        Long fromId,
        String toType,
        Long toId
) {
}

record MappingNodeCreateRequest(
        String nodeType,
        Long companyProfileId,
        String name,
        String splitter,
        BigDecimal latitude,
        BigDecimal longitude
) {
}

record MappingWifiUpdateRequest(
        String wifiName,
        String wifiPassword
) {
}

record MappingNodeUpdateRequest(
        String name,
        String splitter,
        BigDecimal latitude,
        BigDecimal longitude,
        Long companyProfileId
) {
}

record NormalizedLink(
        String fromType,
        Long fromId,
        String toType,
        Long toId
) {
}

record CoordinateValue(
        BigDecimal latitude,
        BigDecimal longitude
) {
}
