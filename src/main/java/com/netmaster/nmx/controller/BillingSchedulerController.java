package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.BillingAutomationSettingsRequest;
import com.netmaster.nmx.dto.BillingInvoiceBulkSendRequest;
import com.netmaster.nmx.dto.BillingInvoiceSendRequest;
import com.netmaster.nmx.dto.BillingPppoeActionRequest;
import com.netmaster.nmx.security.TenantRoleAccess;
import com.netmaster.nmx.service.BillingOperatorContextService;
import com.netmaster.nmx.service.BillingSchedulerJobService;
import com.netmaster.nmx.service.BillingSchedulerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class BillingSchedulerController {

    private final BillingSchedulerService billingSchedulerService;
    private final BillingSchedulerJobService billingSchedulerJobService;
    private final BillingOperatorContextService operatorContextService;

    @GetMapping("/billing/scheduler/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary() {
        return readResponse(() -> billingSchedulerService.getSummary(), "Ringkasan billing scheduler berhasil diambil");
    }

    @GetMapping("/billing/scheduler/upcoming")
    public ResponseEntity<ApiResponse<Object>> getUpcoming(@RequestParam(defaultValue = "7") int days) {
        return readResponse(() -> billingSchedulerService.getUpcomingInvoices(days), "Daftar upcoming invoice berhasil diambil");
    }

    @GetMapping("/billing/customers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCustomers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String invoice_status,
            @RequestParam(required = false) String payment_status,
            @RequestParam(required = false) String pppoe_status,
            @RequestParam(required = false) String send_method,
            @RequestParam(required = false) String due_date_from,
            @RequestParam(required = false) String due_date_to,
            @RequestParam(required = false) String area,
            @RequestParam(required = false) String group,
            @RequestParam(required = false, name = "package") String packageName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dueDate") String sort_by,
            @RequestParam(defaultValue = "asc") String sort_direction) {
        return readResponse(() -> {
            Map<String, String> filters = new LinkedHashMap<>();
            filters.put("search", search);
            filters.put("invoice_status", invoice_status);
            filters.put("payment_status", payment_status);
            filters.put("pppoe_status", pppoe_status);
            filters.put("send_method", send_method);
            filters.put("due_date_from", due_date_from);
            filters.put("due_date_to", due_date_to);
            filters.put("area", area);
            filters.put("group", group);
            filters.put("package", packageName);
            return billingSchedulerService.getCustomerBillingList(filters, page, size, sort_by, sort_direction);
        }, "Daftar billing pelanggan berhasil diambil");
    }

    @PostMapping("/billing/invoices/send")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendInvoice(@Valid @RequestBody BillingInvoiceSendRequest request) {
        return writeResponse(() -> billingSchedulerService.sendInvoice(request.customerId(), request.invoiceId(), Boolean.TRUE.equals(request.force()), "manual"), "Invoice berhasil dikirim");
    }

    @PostMapping("/billing/invoices/send-bulk")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendInvoiceBulk(@Valid @RequestBody BillingInvoiceBulkSendRequest request) {
        return writeResponse(() -> billingSchedulerService.sendInvoiceBulk(request.invoiceIds(), Boolean.TRUE.equals(request.force())), "Invoice bulk berhasil diproses");
    }

    @PostMapping("/billing/invoices/{invoiceId}/resend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> resendInvoice(@PathVariable Long invoiceId) {
        return writeResponse(() -> billingSchedulerService.resendInvoice(invoiceId), "Invoice berhasil dikirim ulang");
    }

    @PostMapping("/billing/payments/{paymentId}/send-receipt")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendReceipt(@PathVariable Long paymentId) {
        return writeResponse(() -> billingSchedulerService.sendReceipt(paymentId), "Struk pembayaran berhasil dikirim");
    }

    @GetMapping("/billing/automation-settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAutomationSettings() {
        return readResponse(() -> billingSchedulerService.getAutomationSettings(), "Pengaturan automation berhasil diambil");
    }

    @PutMapping("/billing/automation-settings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateAutomationSettings(@Valid @RequestBody BillingAutomationSettingsRequest request) {
        return writeResponse(() -> billingSchedulerService.updateAutomationSettings(request), "Pengaturan automation berhasil diperbarui");
    }

    @PostMapping("/customers/{customerId}/pppoe/disable")
    public ResponseEntity<ApiResponse<Map<String, Object>>> disablePppoe(@PathVariable Long customerId,
                                                                         @Valid @RequestBody BillingPppoeActionRequest request) {
        return writeResponse(() -> billingSchedulerService.disablePppoe(customerId, request.reason(), "manual"), "PPPoE pelanggan berhasil dinonaktifkan");
    }

    @PostMapping("/customers/{customerId}/pppoe/enable")
    public ResponseEntity<ApiResponse<Map<String, Object>>> enablePppoe(@PathVariable Long customerId,
                                                                        @Valid @RequestBody BillingPppoeActionRequest request) {
        return writeResponse(() -> billingSchedulerService.enablePppoe(customerId, request.reason(), "manual"), "PPPoE pelanggan berhasil diaktifkan");
    }

    @PostMapping("/network/pppoe/disable-overdue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runDisableOverdue() {
        return writeResponse(
                () -> billingSchedulerJobService.runDisableOverdueNow("manual", operatorContextService.currentActor()),
                "Disable overdue PPPoE berhasil dijalankan"
        );
    }

    @PostMapping("/network/pppoe/enable-paid")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runEnablePaid() {
        return writeResponse(
                () -> billingSchedulerJobService.runEnablePaidNow("manual", operatorContextService.currentActor()),
                "Enable paid PPPoE berhasil dijalankan"
        );
    }

    @GetMapping("/billing/logs/invoice-delivery")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getInvoiceDeliveryLogs() {
        return readResponse(() -> billingSchedulerService.getInvoiceDeliveryLogs(), "Log delivery invoice berhasil diambil");
    }

    @GetMapping("/billing/logs/pppoe-actions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPppoeActionLogs() {
        return readResponse(() -> billingSchedulerService.getPppoeActionLogs(), "Log aksi PPPoE berhasil diambil");
    }

    @GetMapping("/billing/logs/scheduler-runs")
    public ResponseEntity<ApiResponse<Object>> getSchedulerRuns() {
        return readResponse(() -> billingSchedulerService.getSchedulerRuns(), "Log scheduler berhasil diambil");
    }

    private <T> ResponseEntity<ApiResponse<T>> readResponse(Action<T> action, String message) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(message, action.get()));
        } catch (Exception ex) {
            String error = resolveErrorMessage(ex);
            log.warn("Billing scheduler read action failed: {}", error, ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
        }
    }

    private <T> ResponseEntity<ApiResponse<T>> writeResponse(Action<T> action, String message) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(message, action.get()));
        } catch (IllegalStateException ex) {
            String error = resolveErrorMessage(ex);
            log.warn("Billing scheduler write action failed (gateway): {}", error, ex);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(error));
        } catch (Exception ex) {
            String error = resolveErrorMessage(ex);
            log.warn("Billing scheduler write action failed: {}", error, ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
        }
    }

    private String resolveErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "Request gagal diproses.";
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String top = throwable.getMessage();
        String bottom = root.getMessage();
        if (bottom != null && !bottom.isBlank()) {
            if (top == null || top.isBlank() || top.equalsIgnoreCase(bottom)) {
                return bottom;
            }
            return top + " | root cause: " + bottom;
        }
        return (top == null || top.isBlank()) ? "Request gagal diproses." : top;
    }

    private boolean hasPermission(String permission) {
        return switch (permission) {
            case "READ" -> TenantRoleAccess.canRead(SecurityContextHolder.getContext().getAuthentication());
            case "UPDATE" -> TenantRoleAccess.canWrite(SecurityContextHolder.getContext().getAuthentication());
            default -> false;
        };
    }

    @FunctionalInterface
    private interface Action<T> {
        T get();
    }
}
