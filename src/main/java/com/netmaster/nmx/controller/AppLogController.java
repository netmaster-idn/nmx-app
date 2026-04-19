package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ActivityLogRowDTO;
import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.ErrorLogRowDTO;
import com.netmaster.nmx.service.AppLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class AppLogController {

    private final AppLogService appLogService;

    @GetMapping("/activity")
    public ResponseEntity<ApiResponse<List<ActivityLogRowDTO>>> getActivityLogs(
            @RequestParam(name = "limit", defaultValue = "250") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Data log proses berhasil diambil",
                appLogService.getRecentActivityRows(limit)
        ));
    }

    @GetMapping("/errors")
    public ResponseEntity<ApiResponse<List<ErrorLogRowDTO>>> getErrorLogs(
            @RequestParam(name = "limit", defaultValue = "250") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Data log error berhasil diambil",
                appLogService.getRecentErrorRows(limit)
        ));
    }
}
