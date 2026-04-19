package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.CustomerBillingSummaryDTO;
import com.netmaster.nmx.dto.InvoiceRowDTO;
import com.netmaster.nmx.dto.PaymentHistoryItemDTO;
import com.netmaster.nmx.security.TenantRoleAccess;
import com.netmaster.nmx.service.BillingQuickActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerQuickActionController {

    private final BillingQuickActionService billingQuickActionService;

    @GetMapping("/{id}/quick-payments")
    public ResponseEntity<ApiResponse<List<PaymentHistoryItemDTO>>> getPayments(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "History pembayaran berhasil diambil",
                    billingQuickActionService.getCustomerPayments(id, startDate, endDate)
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/{id}/payments/export")
    public ResponseEntity<byte[]> exportPayments(
            @PathVariable Long id,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate
    ) {
        return billingQuickActionService.exportCustomerPayments(id, startDate, endDate);
    }

    @GetMapping("/{id}/billing-invoices")
    public ResponseEntity<ApiResponse<List<InvoiceRowDTO>>> getInvoices(
            @PathVariable Long id,
            @RequestParam(required = false) String status
    ) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Data invoice berhasil diambil",
                    billingQuickActionService.getCustomerInvoices(id, status)
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/{id}/invoices/export")
    public ResponseEntity<byte[]> exportInvoices(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "pdf") String format
    ) {
        return billingQuickActionService.exportCustomerInvoices(id, status, format);
    }

    @GetMapping("/{id}/billing-summary")
    public ResponseEntity<ApiResponse<CustomerBillingSummaryDTO>> getBillingSummary(@PathVariable Long id) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Ringkasan billing pelanggan berhasil diambil",
                    billingQuickActionService.getCustomerBillingSummary(id)
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<ApiResponse<Void>> suspendCustomer(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> payload
    ) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        try {
            String reason = payload != null ? payload.get("reason") : null;
            billingQuickActionService.suspendCustomer(id, reason);
            return ResponseEntity.ok(ApiResponse.success("Layanan pelanggan berhasil disuspend", null));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable Long id) {
        if (!hasPermission("DELETE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        try {
            billingQuickActionService.softDeleteCustomer(id);
            return ResponseEntity.ok(ApiResponse.success("Pelanggan berhasil diterminasi", null));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    private boolean hasPermission(String permission) {
        return switch (permission) {
            case "READ" -> TenantRoleAccess.canRead(SecurityContextHolder.getContext().getAuthentication());
            case "UPDATE" -> TenantRoleAccess.canWrite(SecurityContextHolder.getContext().getAuthentication());
            case "DELETE" -> TenantRoleAccess.canDelete(SecurityContextHolder.getContext().getAuthentication());
            default -> false;
        };
    }
}
