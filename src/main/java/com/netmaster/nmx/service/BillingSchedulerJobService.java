package com.netmaster.nmx.service;

import com.netmaster.nmx.model.BillingAutomationSetting;
import com.netmaster.nmx.model.CustomerBillingStatus;
import com.netmaster.nmx.model.Payment;
import com.netmaster.nmx.model.SchedulerRunLog;
import com.netmaster.nmx.repository.CustomerBillingStatusRepository;
import com.netmaster.nmx.repository.PaymentRepository;
import com.netmaster.nmx.repository.SchedulerRunLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingSchedulerJobService {

    private final BillingAutomationSettingsService settingsService;
    private final BillingSchedulerService schedulerService;
    private final BillingCustomerStatusProjectionService projectionService;
    private final WhatsappReminderAutomationService whatsappReminderAutomationService;
    private final SchedulerRunLogRepository schedulerRunLogRepository;
    private final CustomerBillingStatusRepository billingStatusRepository;
    private final PaymentRepository paymentRepository;

    @Transactional
    public void processRecurringAutomation() {
        projectionService.refreshAll();
        whatsappReminderAutomationService.dispatchScheduledInvoiceReminders();
        BillingAutomationSetting settings = settingsService.getOrCreate();
        if (!Boolean.TRUE.equals(settings.getIsActive())) {
            return;
        }
        runAutoSendInvoices(settings);
        runAutoSendReceipts(settings);
        runDisableOverdue(settings);
        runEnablePaid(settings);
    }

    @Transactional
    public Map<String, Object> runDisableOverdueNow(String executionMode, String triggeredBy) {
        return runJob("DisableOverduePppoeJob", executionMode, triggeredBy, schedulerService::autoDisableOverdueCustomers);
    }

    @Transactional
    public Map<String, Object> runEnablePaidNow(String executionMode, String triggeredBy) {
        return runJob("EnablePaidPppoeJob", executionMode, triggeredBy, schedulerService::autoEnablePaidCustomers);
    }

    @Transactional
    public void syncProjectionJob() {
        runJob("SyncCustomerBillingStatusJob", "system", "system", () -> {
            projectionService.refreshAll();
            return Map.of("success", 1, "failed", 0, "results", List.of());
        });
    }

    private void runAutoSendInvoices(BillingAutomationSetting settings) {
        if (!Boolean.TRUE.equals(settings.getAutoSendInvoice())) {
            return;
        }
        runJob("AutoSendInvoicesJob", "system", "system", () -> {
            List<Map<String, Object>> results = new ArrayList<>();
            int success = 0;
            int failed = 0;
            for (Map<String, Object> row : schedulerService.getUpcomingInvoices(settings.getInvoiceSendDaysBeforeDue())) {
                try {
                    if (row.get("sendDate") instanceof java.time.LocalDate sendDate && sendDate.isAfter(java.time.LocalDate.now())) {
                        continue;
                    }
                    String invoiceStatus = String.valueOf(row.get("invoiceStatus"));
                    if (!List.of("scheduled", "draft", "failed").contains(invoiceStatus)) {
                        continue;
                    }
                    results.add(schedulerService.sendInvoice((Long) row.get("customerId"), (Long) row.get("invoiceId"), false, "auto"));
                    success++;
                } catch (Exception ex) {
                    failed++;
                    results.add(Map.of("invoiceId", row.get("invoiceId"), "status", "failed", "message", ex.getMessage()));
                }
            }
            return Map.of("success", success, "failed", failed, "results", results);
        });
    }

    private void runAutoSendReceipts(BillingAutomationSetting settings) {
        if (!Boolean.TRUE.equals(settings.getAutoSendReceipt())) {
            return;
        }
        runJob("SendPaymentReceiptJob", "system", "system", () -> {
            List<Map<String, Object>> results = new ArrayList<>();
            int success = 0;
            int failed = 0;
            for (Payment payment : paymentRepository.findAll()) {
                if (payment.getReceiptSentAt() != null || !"paid".equalsIgnoreCase(payment.getStatus())) {
                    continue;
                }
                try {
                    results.add(schedulerService.sendReceipt(payment.getId()));
                    success++;
                } catch (Exception ex) {
                    failed++;
                    results.add(Map.of("paymentId", payment.getId(), "status", "failed", "message", ex.getMessage()));
                }
            }
            return Map.of("success", success, "failed", failed, "results", results);
        });
    }

    private void runDisableOverdue(BillingAutomationSetting settings) {
        if (Boolean.TRUE.equals(settings.getAutoDisablePppoe())) {
            runDisableOverdueNow("system", "system");
        }
    }

    private void runEnablePaid(BillingAutomationSetting settings) {
        if (Boolean.TRUE.equals(settings.getAutoEnablePppoeAfterPayment())) {
            runEnablePaidNow("system", "system");
        }
    }

    private Map<String, Object> runJob(String jobName,
                                       String executionMode,
                                       String triggeredBy,
                                       JobExecutor executor) {
        SchedulerRunLog runLog = new SchedulerRunLog();
        runLog.setJobName(jobName);
        runLog.setExecutionMode(executionMode);
        runLog.setTriggeredBy(triggeredBy);
        runLog.setStatus("running");
        runLog.setStartedAt(LocalDateTime.now());
        schedulerRunLogRepository.save(runLog);

        try {
            Map<String, Object> result = executor.execute();
            runLog.setStatus("success");
            runLog.setFinishedAt(LocalDateTime.now());
            runLog.setSuccessCount(asInt(result.get("success")));
            runLog.setFailedCount(asInt(result.get("failed")));
            runLog.setProcessedCount(runLog.getSuccessCount() + runLog.getFailedCount());
            schedulerRunLogRepository.save(runLog);
            return result;
        } catch (Exception ex) {
            log.warn("Job {} failed: {}", jobName, ex.getMessage());
            runLog.setStatus("failed");
            runLog.setFinishedAt(LocalDateTime.now());
            runLog.setErrorSummary(ex.getMessage());
            schedulerRunLogRepository.save(runLog);
            throw ex;
        }
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    @FunctionalInterface
    private interface JobExecutor {
        Map<String, Object> execute();
    }
}
