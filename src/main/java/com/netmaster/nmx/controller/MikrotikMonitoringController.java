package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.service.MikrotikMonitoringSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring/mikrotik")
@RequiredArgsConstructor
public class MikrotikMonitoringController {

    private final MikrotikMonitoringSnapshotService snapshotService;

    @GetMapping("/routers")
    public ResponseEntity<ApiResponse<MikrotikMonitoringSnapshotService.RouterListResponse>> getRouters() {
        return ResponseEntity.ok(ApiResponse.success("Router monitoring berhasil diambil", snapshotService.getRouters()));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<MikrotikMonitoringSnapshotService.MonitoringSummaryResponse>> getSummary(
            @RequestParam(required = false) Long routerId) {
        return ResponseEntity.ok(ApiResponse.success("Summary monitoring berhasil diambil", snapshotService.getSummary(routerId)));
    }

    @GetMapping("/ether1-traffic")
    public ResponseEntity<ApiResponse<MikrotikMonitoringSnapshotService.TrafficResponse>> getEther1Traffic(
            @RequestParam(required = false) Long routerId) {
        return ResponseEntity.ok(ApiResponse.success("Traffic ether1 berhasil diambil", snapshotService.getEther1Traffic(routerId)));
    }

    @GetMapping("/resources")
    public ResponseEntity<ApiResponse<MikrotikMonitoringSnapshotService.ResourceResponse>> getResources(
            @RequestParam(required = false) Long routerId) {
        return ResponseEntity.ok(ApiResponse.success("Resource monitoring berhasil diambil", snapshotService.getResources(routerId)));
    }

    @GetMapping("/pppoe")
    public ResponseEntity<ApiResponse<MikrotikMonitoringSnapshotService.PppoeListResponse>> getPppoe(
            @RequestParam(required = false) Long routerId) {
        return ResponseEntity.ok(ApiResponse.success("PPPoE monitoring berhasil diambil", snapshotService.getPppoe(routerId)));
    }

    @GetMapping("/pppoe/{name}/traffic")
    public ResponseEntity<ApiResponse<MikrotikMonitoringSnapshotService.UserTrafficResponse>> getPppoeTraffic(
            @PathVariable("name") String name,
            @RequestParam(required = false) Long routerId) {
        return ResponseEntity.ok(ApiResponse.success("Traffic PPPoE berhasil diambil", snapshotService.getPppoeUserTraffic(routerId, name)));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<MikrotikMonitoringSnapshotService.LogResponse>> getLogs(
            @RequestParam(required = false) Long routerId) {
        return ResponseEntity.ok(ApiResponse.success("Log monitoring berhasil diambil", snapshotService.getLogs(routerId)));
    }
}
