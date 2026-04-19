package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.model.IpPool;
import com.netmaster.nmx.repository.IpPoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/network/ip-pool")
@RequiredArgsConstructor
public class IpPoolController {

    private final IpPoolRepository ipPoolRepository;

    // Get all IP Pools
    @GetMapping
    public ResponseEntity<ApiResponse<List<IpPool>>> getAllPools() {
        List<IpPool> pools = ipPoolRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success("Data IP Pool berhasil diambil", pools));
    }

    // Get IP Pool by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IpPool>> getPool(@PathVariable Long id) {
        return ipPoolRepository.findById(id)
                .map(pool -> ResponseEntity.ok(ApiResponse.success("IP Pool ditemukan", pool)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("IP Pool tidak ditemukan")));
    }

    // Create new IP Pool
    @PostMapping
    public ResponseEntity<ApiResponse<IpPool>> createPool(@RequestBody IpPool pool) {
        IpPool saved = ipPoolRepository.save(pool);
        return ResponseEntity.ok(ApiResponse.success("IP Pool berhasil dibuat", saved));
    }

    // Update IP Pool
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<IpPool>> updatePool(@PathVariable Long id, @RequestBody IpPool pool) {
        if (!ipPoolRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("IP Pool tidak ditemukan"));
        }
        pool.setId(id);
        IpPool updated = ipPoolRepository.save(pool);
        return ResponseEntity.ok(ApiResponse.success("IP Pool berhasil diperbarui", updated));
    }

    // Delete IP Pool
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePool(@PathVariable Long id) {
        if (!ipPoolRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("IP Pool tidak ditemukan"));
        }
        ipPoolRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("IP Pool berhasil dihapus", null));
    }

    // Get IP Pool statistics
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getStats() {
        List<IpPool> all = ipPoolRepository.findAll();
        long total = all.size();
        long active = all.stream().filter(p -> p.isActive()).count();

        return ResponseEntity.ok(ApiResponse.success("Statistik IP Pool", java.util.Map.of(
                "total", total,
                "active", active
        )));
    }

    // Search IP Pools
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<IpPool>>> searchPools(@RequestParam String keyword) {
        List<IpPool> pools = ipPoolRepository.findAll().stream()
                .filter(p -> p.getName().toLowerCase().contains(keyword.toLowerCase()) ||
                        (p.getPoolName() != null && p.getPoolName().toLowerCase().contains(keyword.toLowerCase())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Hasil pencarian", pools));
    }
}

