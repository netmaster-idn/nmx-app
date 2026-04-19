package com.netmaster.nmx.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MikrotikHybridScheduler {

    private final MikrotikMonitoringManager mikrotikMonitoringManager;
    private final MikrotikMonitoringQueryService mikrotikMonitoringQueryService;

    @Scheduled(fixedDelay = 5000, initialDelay = 15000)
    public void runMonitoringPoller() {
        mikrotikMonitoringManager.pollDueMonitoringDevices();
    }

    public void runSnmpPoller() {
        runMonitoringPoller();
    }

    @Scheduled(fixedDelay = 7000, initialDelay = 20000)
    public void runApiSyncWorker() {
        mikrotikMonitoringManager.syncDueApiDevices();
    }

    @Scheduled(cron = "0 15 * * * *")
    public void pruneMonitoringHistory() {
        mikrotikMonitoringQueryService.pruneOldData();
    }
}
