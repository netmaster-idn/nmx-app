package com.netmaster.nmx.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BillingAutomationScheduler {

    private final BillingQuickActionService billingQuickActionService;
    private final BillingSchedulerJobService billingSchedulerJobService;

    @Scheduled(cron = "${nmx.billing.auto-billing-cron:0 15 0 1 * *}", zone = "${nmx.billing.scheduler-zone:Asia/Makassar}")
    public void generateMonthlyInvoices() {
        billingQuickActionService.generateMonthlyInvoices();
    }

    @Scheduled(cron = "${nmx.billing.auto-suspend-cron:0 0 2 * * *}", zone = "${nmx.billing.scheduler-zone:Asia/Makassar}")
    public void autoSuspendOverdueCustomers() {
        billingSchedulerJobService.runDisableOverdueNow("system", "system");
    }

    @Scheduled(cron = "${nmx.whatsapp.reminder-cron:0 */10 1-23 * * *}", zone = "${nmx.billing.scheduler-zone:Asia/Makassar}")
    public void processBillingAutomation() {
        billingSchedulerJobService.processRecurringAutomation();
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "${nmx.billing.scheduler-zone:Asia/Makassar}")
    public void syncBillingSchedulerProjection() {
        billingSchedulerJobService.syncProjectionJob();
    }
}
