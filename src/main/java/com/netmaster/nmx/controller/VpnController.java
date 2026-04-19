package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.model.VpnConnection;
import com.netmaster.nmx.repository.VpnConnectionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/network/vpn")
@RequiredArgsConstructor
public class VpnController {

    private final VpnConnectionRepository vpnRepository;

    // Get all VPN connections
    @GetMapping
    public ResponseEntity<ApiResponse<List<VpnConnection>>> getAllConnections() {
        List<VpnConnection> connections = vpnRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success("Data VPN berhasil diambil", connections));
    }

    // Get connection by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<VpnConnection>> getConnection(@PathVariable Long id) {
        return vpnRepository.findById(id)
                .map(conn -> ResponseEntity.ok(ApiResponse.success("VPN ditemukan", conn)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("VPN tidak ditemukan")));
    }

    // Create new VPN connection
    @PostMapping
    public ResponseEntity<ApiResponse<VpnConnection>> createConnection(@RequestBody VpnConnection connection) {
        VpnConnection saved = vpnRepository.save(connection);
        return ResponseEntity.ok(ApiResponse.success("VPN berhasil dibuat", saved));
    }

    // Update VPN connection
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<VpnConnection>> updateConnection(@PathVariable Long id, @RequestBody VpnConnection connection) {
        if (!vpnRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("VPN tidak ditemukan"));
        }
        connection.setId(id);
        VpnConnection updated = vpnRepository.save(connection);
        return ResponseEntity.ok(ApiResponse.success("VPN berhasil diperbarui", updated));
    }

    // Delete VPN connection
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteConnection(@PathVariable Long id) {
        if (!vpnRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("VPN tidak ditemukan"));
        }
        vpnRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("VPN berhasil dihapus", null));
    }

    // Get VPN statistics
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getStats() {
        List<VpnConnection> all = vpnRepository.findAll();
        long total = all.size();
        long connected = all.stream().filter(c -> "connected".equals(c.getStatus())).count();
        long disconnected = all.stream().filter(c -> "disconnected".equals(c.getStatus())).count();

        return ResponseEntity.ok(ApiResponse.success("Statistik VPN", java.util.Map.of(
                "total", total,
                "connected", connected,
                "disconnected", disconnected
        )));
    }

    // Search VPN connections
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<VpnConnection>>> searchConnections(@RequestParam String keyword) {
        List<VpnConnection> connections = vpnRepository.findAll().stream()
                .filter(c -> c.getName().toLowerCase().contains(keyword.toLowerCase()) ||
                        (c.getRemoteIp() != null && c.getRemoteIp().contains(keyword)))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Hasil pencarian", connections));
    }
}

