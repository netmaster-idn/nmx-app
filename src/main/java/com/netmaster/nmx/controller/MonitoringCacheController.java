package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.service.DeviceStatusCacheService;
import com.netmaster.nmx.service.MikrotikMonitoringQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class MonitoringCacheController {

    private final MikrotikMonitoringQueryService queryService;
    private final DeviceStatusCacheService deviceStatusCacheService;

    @GetMapping("/interfaces/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInterfaceSummary() {
        return ResponseEntity.ok(ApiResponse.success("Interface summary", queryService.getInterfaceSummary()));
    }

    @GetMapping("/interfaces/top")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTopInterfaces(@RequestParam(defaultValue = "5") Integer limit,
                                                                             @RequestParam(required = false) Long deviceId) {
        return ResponseEntity.ok(ApiResponse.success("Top interfaces", queryService.getTopInterfaces(limit, deviceId)));
    }

    @GetMapping("/interfaces")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInterfaces(@RequestParam(defaultValue = "0") int page,
                                                                          @RequestParam(defaultValue = "10") int size,
                                                                          @RequestParam(required = false) Long deviceId,
                                                                          @RequestParam(required = false) String status,
                                                                          @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success("Interfaces", queryService.getInterfaces(page, size, deviceId, status, search)));
    }

    @GetMapping("/interfaces/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInterfaceDetail(@PathVariable Long id,
                                                                               @RequestParam(defaultValue = "60") int historyMinutes) {
        return ResponseEntity.ok(ApiResponse.success("Interface detail", queryService.getInterfaceDetail(id, historyMinutes)));
    }

    @GetMapping("/interfaces/{id}/history")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInterfaceHistory(@PathVariable Long id,
                                                                                @RequestParam(defaultValue = "60") int minutes) {
        return ResponseEntity.ok(ApiResponse.success("Interface history", queryService.getInterfaceHistory(id, minutes)));
    }

    @GetMapping("/pppoe/active/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPppoeActiveSummary(@RequestParam(required = false) String search,
                                                                                  @RequestParam(required = false) String type,
                                                                                  @RequestParam(required = false) String location,
                                                                                  @RequestParam(required = false) String status,
                                                                                  @RequestParam(required = false) String freshness) {
        return ResponseEntity.ok(ApiResponse.success("PPPoE active summary", queryService.getPppoeActiveSummary(
                deviceStatusCacheService.getMatchingMikrotikSourceIds(search, type, location, status, freshness)
        )));
    }

    @GetMapping("/pppoe/active")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPppoeActive(@RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "10") int size,
                                                                           @RequestParam(required = false) Long deviceId,
                                                                           @RequestParam(required = false) String profile,
                                                                           @RequestParam(required = false) String status,
                                                                           @RequestParam(required = false) String search,
                                                                           @RequestParam(required = false) String type,
                                                                           @RequestParam(required = false) String location,
                                                                           @RequestParam(required = false) String freshness) {
        return ResponseEntity.ok(ApiResponse.success("PPPoE active sessions", queryService.getPppoeActive(
                page,
                size,
                deviceId,
                profile,
                status,
                search,
                deviceStatusCacheService.getMatchingMikrotikSourceIds(search, type, location, status, freshness)
        )));
    }

    @GetMapping("/pppoe/active/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPppoeActiveDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("PPPoE active detail", queryService.getPppoeActiveDetail(id)));
    }

    @GetMapping("/pppoe/active/profiles")
    public ResponseEntity<ApiResponse<List<String>>> getPppoeProfiles(@RequestParam(required = false) String search,
                                                                      @RequestParam(required = false) String type,
                                                                      @RequestParam(required = false) String location,
                                                                      @RequestParam(required = false) String status,
                                                                      @RequestParam(required = false) String freshness) {
        return ResponseEntity.ok(ApiResponse.success("PPPoE profiles", queryService.getPppoeProfiles(
                deviceStatusCacheService.getMatchingMikrotikSourceIds(search, type, location, status, freshness)
        )));
    }

    @GetMapping("/pppoe/active/devices")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPppoeDevices(@RequestParam(required = false) String search,
                                                                                  @RequestParam(required = false) String type,
                                                                                  @RequestParam(required = false) String location,
                                                                                  @RequestParam(required = false) String status,
                                                                                  @RequestParam(required = false) String freshness) {
        return ResponseEntity.ok(ApiResponse.success("PPPoE devices", queryService.getPppoeDevices(
                deviceStatusCacheService.getMatchingMikrotikSourceIds(search, type, location, status, freshness)
        )));
    }

    @GetMapping("/pppoe/events/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPppoeEventSummary(@RequestParam(required = false) String search,
                                                                                 @RequestParam(required = false) String type,
                                                                                 @RequestParam(required = false) String location,
                                                                                 @RequestParam(required = false) String status,
                                                                                 @RequestParam(required = false) String freshness) {
        return ResponseEntity.ok(ApiResponse.success("PPPoE event summary", queryService.getPppoeEventSummary(
                deviceStatusCacheService.getMatchingMikrotikSourceIds(search, type, location, status, freshness)
        )));
    }

    @GetMapping("/pppoe/events")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPppoeEvents(@RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "10") int size,
                                                                           @RequestParam(required = false) Long deviceId,
                                                                           @RequestParam(required = false) String eventType,
                                                                           @RequestParam(required = false) String username,
                                                                           @RequestParam(required = false) String search,
                                                                           @RequestParam(required = false) String type,
                                                                           @RequestParam(required = false) String location,
                                                                           @RequestParam(required = false) String status,
                                                                           @RequestParam(required = false) String freshness) {
        return ResponseEntity.ok(ApiResponse.success("PPPoE events", queryService.getPppoeEvents(
                page,
                size,
                deviceId,
                eventType,
                username,
                search,
                deviceStatusCacheService.getMatchingMikrotikSourceIds(search, type, location, status, freshness)
        )));
    }

    @GetMapping("/pppoe/events/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPppoeEventDetail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("PPPoE event detail", queryService.getPppoeEventDetail(id)));
    }

    @GetMapping("/pppoe/events/types")
    public ResponseEntity<ApiResponse<List<String>>> getPppoeEventTypes(@RequestParam(required = false) String search,
                                                                        @RequestParam(required = false) String type,
                                                                        @RequestParam(required = false) String location,
                                                                        @RequestParam(required = false) String status,
                                                                        @RequestParam(required = false) String freshness) {
        return ResponseEntity.ok(ApiResponse.success("PPPoE event types", queryService.getPppoeEventTypes(
                deviceStatusCacheService.getMatchingMikrotikSourceIds(search, type, location, status, freshness)
        )));
    }

    @GetMapping("/pppoe/events/devices")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPppoeEventDevices(@RequestParam(required = false) String search,
                                                                                       @RequestParam(required = false) String type,
                                                                                       @RequestParam(required = false) String location,
                                                                                       @RequestParam(required = false) String status,
                                                                                       @RequestParam(required = false) String freshness) {
        return ResponseEntity.ok(ApiResponse.success("PPPoE event devices", queryService.getPppoeDevices(
                deviceStatusCacheService.getMatchingMikrotikSourceIds(search, type, location, status, freshness)
        )));
    }
}
