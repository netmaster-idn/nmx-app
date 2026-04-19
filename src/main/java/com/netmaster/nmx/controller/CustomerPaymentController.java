package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.InvoiceRowDTO;
import com.netmaster.nmx.dto.PaymentRecordDTO;
import com.netmaster.nmx.security.TenantRoleAccess;
import com.netmaster.nmx.service.BillingInvoiceService;
import com.netmaster.nmx.service.PaymentManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerPaymentController {

    private final PaymentManagementService paymentManagementService;
    private final BillingInvoiceService billingInvoiceService;

    @GetMapping("/{customerId}/payments")
    public ResponseEntity<ApiResponse<List<PaymentRecordDTO>>> getCustomerPayments(
            @PathVariable Long customerId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month
    ) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        return ResponseEntity.ok(ApiResponse.success("Riwayat pembayaran pelanggan berhasil diambil", paymentManagementService.getPaymentsByCustomer(customerId, year, month)));
    }

    @GetMapping("/{customerId}/payments/years")
    public ResponseEntity<ApiResponse<List<Integer>>> getCustomerPaymentYears(@PathVariable Long customerId) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        return ResponseEntity.ok(ApiResponse.success("Data tahun pembayaran berhasil diambil", paymentManagementService.getDistinctYearsByCustomer(customerId)));
    }

    @GetMapping("/{customerId}/payments/months")
    public ResponseEntity<ApiResponse<List<Integer>>> getCustomerPaymentMonths(
            @PathVariable Long customerId,
            @RequestParam Integer year
    ) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        return ResponseEntity.ok(ApiResponse.success("Data bulan pembayaran berhasil diambil", paymentManagementService.getDistinctMonthsByCustomerAndYear(customerId, year)));
    }

    @GetMapping("/{customerId}/invoices")
    public ResponseEntity<ApiResponse<List<InvoiceRowDTO>>> getCustomerInvoices(@PathVariable Long customerId) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        return ResponseEntity.ok(ApiResponse.success("Data invoice pelanggan berhasil diambil", billingInvoiceService.getInvoiceRowsByCustomer(customerId)));
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
