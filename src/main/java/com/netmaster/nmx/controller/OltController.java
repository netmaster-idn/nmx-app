package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.model.OltDevice;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/network/olt")
@RequiredArgsConstructor
public class OltController {

    private <T> ResponseEntity<ApiResponse<T>> oltDisabled() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Fitur OLT dinonaktifkan"));
    }

    // Get all OLT devices
    @GetMapping
    public ResponseEntity<ApiResponse<List<OltDevice>>> getAllDevices(
            @RequestParam(required = false) String keyword) {
        return oltDisabled();
    }

    // Get device by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OltDevice>> getDevice(@PathVariable Long id) {
        return oltDisabled();
    }

    // Create new OLT device
    @PostMapping
    public ResponseEntity<ApiResponse<OltDevice>> createDevice(@RequestBody OltDevice request) {
        return oltDisabled();
    }

    // Update OLT device
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<OltDevice>> updateDevice(@PathVariable Long id, @RequestBody OltDevice request) {
        return oltDisabled();
    }

    // Delete OLT device
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDevice(@PathVariable Long id) {
        return oltDisabled();
    }

    // Get OLT statistics
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getStats() {
        return oltDisabled();
    }

    // Search OLT devices
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<OltDevice>>> searchDevices(@RequestParam String keyword) {
        return oltDisabled();
    }
}

