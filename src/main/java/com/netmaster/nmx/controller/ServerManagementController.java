package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.model.MikrotikDevice;
import com.netmaster.nmx.model.Server;
import com.netmaster.nmx.repository.MikrotikDeviceRepository;
import com.netmaster.nmx.repository.OdcRepository;
import com.netmaster.nmx.repository.ServerRepository;
import com.netmaster.nmx.security.TenantRoleAccess;
import com.netmaster.nmx.service.MikrotikConnectionService;
import com.netmaster.nmx.service.MikrotikStatusPingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class ServerManagementController {

    private final ServerRepository serverRepository;
    private final MikrotikDeviceRepository mikrotikRepository;
    private final OdcRepository odcRepository;
    private final MikrotikStatusPingService mikrotikStatusPingService;
    private final MikrotikConnectionService mikrotikConnectionService;

    private boolean hasPermission(String permission) {
        return switch (permission) {
            case "READ" -> TenantRoleAccess.canRead(SecurityContextHolder.getContext().getAuthentication());
            case "WRITE" -> TenantRoleAccess.canDelete(SecurityContextHolder.getContext().getAuthentication());
            default -> false;
        };
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Server>>> getAllServers() {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        List<Server> servers = serverRepository.findByIsActiveTrueOrderByNameAsc();
        servers.forEach(this::applyMikrotikMetadata);
        return ResponseEntity.ok(ApiResponse.success("Data server berhasil diambil", servers));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Server>> getServerById(@PathVariable Long id) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        return serverRepository.findById(id)
                .map(server -> {
                    applyMikrotikMetadata(server);
                    return ResponseEntity.ok(ApiResponse.success("Data server ditemukan", server));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Server tidak ditemukan")));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Server>> createServer(@RequestBody Server server) {
        if (!hasPermission("WRITE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Tenant Super Admin only"));
        }
        if (server.getName() == null || server.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Nama server wajib diisi"));
        }
        if (server.getMikrotikId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Mikrotik server wajib dipilih"));
        }
        server.setIsActive(true);
        try {
            syncServerFromMikrotik(server);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
        Server saved = serverRepository.save(server);
        applyMikrotikMetadata(saved);
        return ResponseEntity.ok(ApiResponse.success("Server berhasil dibuat", saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Server>> updateServer(@PathVariable Long id, @RequestBody Server server) {
        if (!hasPermission("WRITE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Tenant Super Admin only"));
        }
        if (server.getName() == null || server.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Nama server wajib diisi"));
        }
        if (server.getMikrotikId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Mikrotik server wajib dipilih"));
        }
        return serverRepository.findById(id)
                .map(existing -> {
                    existing.setName(server.getName());
                    existing.setLocation(server.getLocation());
                    existing.setRegion(server.getRegion());
                    existing.setMikrotikId(server.getMikrotikId());
                    existing.setLatitude(server.getLatitude());
                    existing.setLongitude(server.getLongitude());
                    try {
                        syncServerFromMikrotik(existing);
                    } catch (IllegalArgumentException ex) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.<Server>error(ex.getMessage()));
                    }
                    Server saved = serverRepository.save(existing);
                    applyMikrotikMetadata(saved);
                    return ResponseEntity.ok(ApiResponse.success("Server berhasil diperbarui", saved));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Server tidak ditemukan")));
    }

    @PostMapping("/{id}/sync-mikrotik")
    public ResponseEntity<ApiResponse<Server>> syncServerMikrotik(@PathVariable Long id) {
        if (!hasPermission("WRITE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Tenant Super Admin only"));
        }
        return serverRepository.findById(id)
                .map(server -> {
                    try {
                        syncServerFromMikrotik(server);
                    } catch (IllegalArgumentException ex) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.<Server>error(ex.getMessage()));
                    }
                    Server saved = serverRepository.save(server);
                    applyMikrotikMetadata(saved);
                    return ResponseEntity.ok(ApiResponse.success("Sinkronisasi server berhasil", saved));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Server tidak ditemukan")));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteServer(@PathVariable Long id) {
        if (!hasPermission("WRITE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Tenant Super Admin only"));
        }
        Server server = serverRepository.findById(id).orElse(null);
        if (server == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Server tidak ditemukan"));
        }
        if (!odcRepository.findByServerIdOrderByNameAsc(id).isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Server masih dipakai oleh ODC/ODP. Hapus atau pindahkan ODC/ODP terlebih dahulu."));
        }

        serverRepository.delete(server);
        return ResponseEntity.ok(ApiResponse.success("Server berhasil dihapus permanen.", null));
    }

    private void syncServerFromMikrotik(Server server) {
        if (server.getMikrotikId() == null) {
            throw new IllegalArgumentException("Mikrotik server wajib dipilih");
        }
        MikrotikDevice mikrotik = mikrotikRepository.findById(server.getMikrotikId())
                .orElseThrow(() -> new IllegalArgumentException("Mikrotik server tidak ditemukan"));
        MikrotikDevice refreshed = mikrotikStatusPingService.refreshDeviceStatus(mikrotik.getId());
        server.setIpAddress(resolveRemoteManagementIp(refreshed));
    }

    private void applyMikrotikMetadata(Server server) {
        if (server.getMikrotikId() == null) {
            server.setMikrotikName(null);
            server.setMikrotikStatus("unknown");
            return;
        }
        mikrotikRepository.findById(server.getMikrotikId()).ifPresentOrElse(mikrotik -> {
            server.setMikrotikName(mikrotik.getName());
            server.setMikrotikStatus(mikrotik.getStatus());
            String managementIp = resolveRemoteManagementIp(mikrotik);
            if (managementIp != null) {
                server.setIpAddress(managementIp);
            }
        }, () -> {
            server.setMikrotikName(null);
            server.setMikrotikStatus("unknown");
        });
    }

    private String resolveRemoteManagementIp(MikrotikDevice mikrotik) {
        String[] candidates = {
                extractHost(mikrotik.getApiIpAddress()),
                extractHost(mikrotik.getWinboxIpAddress()),
                normalize(mikrotik.getIpAddress()),
                extractHost(mikrotik.resolveVpnHost())
        };
        for (String candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private String extractHost(String value) {
        return normalize(mikrotikConnectionService.extractHost(value));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
