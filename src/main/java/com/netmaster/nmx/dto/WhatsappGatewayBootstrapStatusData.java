package com.netmaster.nmx.dto;

import java.time.LocalDateTime;

public record WhatsappGatewayBootstrapStatusData(
        boolean sourceAvailable,
        boolean installed,
        boolean runtimeAvailable,
        boolean reachable,
        boolean installationRunning,
        String installState,
        String installMessage,
        String gatewayDirectory,
        boolean bootstrapEnabled,
        boolean autoInstallOnStartup,
        LocalDateTime installUpdatedAt
) {
}
