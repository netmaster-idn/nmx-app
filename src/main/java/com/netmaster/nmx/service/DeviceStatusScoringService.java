package com.netmaster.nmx.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class DeviceStatusScoringService {

    public int calculateHealthScore(String status,
                                    String freshness,
                                    boolean hasActiveMaintenance,
                                    int activeAlertCount,
                                    BigDecimal cpuUsage,
                                    BigDecimal memoryUsage,
                                    BigDecimal latencyMs,
                                    BigDecimal packetLoss) {
        if (hasActiveMaintenance) {
            return 100;
        }

        int score = 100;
        String safeStatus = status == null ? "unknown" : status.trim().toLowerCase();
        String safeFreshness = freshness == null ? "unreachable" : freshness.trim().toLowerCase();

        if ("offline".equals(safeStatus)) {
            score -= 55;
        } else if ("warning".equals(safeStatus)) {
            score -= 20;
        } else if ("unknown".equals(safeStatus)) {
            score -= 35;
        }

        if ("stale".equals(safeFreshness)) {
            score -= 20;
        } else if ("delayed".equals(safeFreshness)) {
            score -= 10;
        } else if ("unreachable".equals(safeFreshness)) {
            score -= 35;
        }

        score -= Math.min(activeAlertCount * 8, 24);
        score -= penaltyForPercent(cpuUsage, 70, 90, 8, 18);
        score -= penaltyForPercent(memoryUsage, 80, 95, 8, 18);
        score -= penaltyForValue(latencyMs, 50, 120, 6, 12);
        score -= penaltyForValue(packetLoss, 1, 5, 6, 12);

        return Math.max(0, Math.min(100, score));
    }

    public String classifyHealth(int score) {
        if (score >= 90) {
            return "healthy";
        }
        if (score >= 70) {
            return "watch";
        }
        return "critical";
    }

    private int penaltyForPercent(BigDecimal value, int warningThreshold, int criticalThreshold, int warningPenalty, int criticalPenalty) {
        if (value == null) {
            return 0;
        }
        int intValue = value.intValue();
        if (intValue >= criticalThreshold) {
            return criticalPenalty;
        }
        if (intValue >= warningThreshold) {
            return warningPenalty;
        }
        return 0;
    }

    private int penaltyForValue(BigDecimal value, int warningThreshold, int criticalThreshold, int warningPenalty, int criticalPenalty) {
        if (value == null) {
            return 0;
        }
        int intValue = value.intValue();
        if (intValue >= criticalThreshold) {
            return criticalPenalty;
        }
        if (intValue >= warningThreshold) {
            return warningPenalty;
        }
        return 0;
    }
}
