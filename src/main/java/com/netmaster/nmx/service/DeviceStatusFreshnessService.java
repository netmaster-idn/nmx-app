package com.netmaster.nmx.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class DeviceStatusFreshnessService {

    public String evaluate(LocalDateTime lastSeenAt, String deviceStatus) {
        if (lastSeenAt == null) {
            return "unreachable";
        }

        long ageSeconds = Duration.between(lastSeenAt, LocalDateTime.now()).getSeconds();
        String safeStatus = deviceStatus == null ? "unknown" : deviceStatus.trim().toLowerCase();

        if ("offline".equals(safeStatus) && ageSeconds > 300) {
            return "unreachable";
        }
        if (ageSeconds < 120) {
            return "fresh";
        }
        if (ageSeconds < 300) {
            return "delayed";
        }
        return "stale";
    }
}
