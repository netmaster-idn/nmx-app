package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.InvoiceDTO;
import com.netmaster.nmx.dto.InvoiceRowDTO;
import com.netmaster.nmx.dto.PaymentRecordDTO;
import com.netmaster.nmx.dto.QuickPayInvoiceRequest;
import com.netmaster.nmx.dto.RecordPaymentRequest;
import com.netmaster.nmx.model.Invoice;
import com.netmaster.nmx.security.TenantRoleAccess;
import com.netmaster.nmx.service.BillingInvoiceService;
import com.netmaster.nmx.service.BillingQuickActionService;
import com.netmaster.nmx.service.PaymentManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.time.LocalDate;
import java.util.Locale;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class BillingInvoiceController {

    private final BillingInvoiceService billingInvoiceService;
    private final PaymentManagementService paymentManagementService;
    private final BillingQuickActionService billingQuickActionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<InvoiceRowDTO>>> getInvoices(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String dueDay,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search
    ) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }

        Integer selectedDueDay = parseDueDay(dueDay);
        List<InvoiceRowDTO> invoices = billingInvoiceService.getAllInvoiceRows().stream()
                .filter(invoice -> invoiceMatchesDate(invoice, selectedDueDay, year, month))
                .filter(invoice -> invoiceMatchesStatus(invoice, status))
                .filter(invoice -> invoiceMatchesSearch(invoice, search))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Data invoice berhasil diambil", invoices));
    }

    @GetMapping("/years")
    public ResponseEntity<ApiResponse<List<Integer>>> getInvoiceYears() {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        return ResponseEntity.ok(ApiResponse.success("Data tahun invoice berhasil diambil", billingInvoiceService.getDistinctInvoiceYears()));
    }

    @GetMapping("/months")
    public ResponseEntity<ApiResponse<List<Integer>>> getInvoiceMonths(@RequestParam Integer year) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        return ResponseEntity.ok(ApiResponse.success("Data bulan invoice berhasil diambil", billingInvoiceService.getDistinctInvoiceMonthsByYear(year)));
    }

    @GetMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> getInvoice(@PathVariable Long id) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }

        try {
            return ResponseEntity.ok(ApiResponse.success("Data invoice ditemukan", billingInvoiceService.getInvoiceRowById(id)));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> createInvoice(@Valid @RequestBody InvoiceDTO dto) {
        if (!hasPermission("CREATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }

        try {
            Invoice invoice = billingInvoiceService.createInvoice(dto);
            return ResponseEntity.ok(ApiResponse.success("Invoice berhasil dibuat!", billingInvoiceService.getInvoiceRowById(invoice.getId())));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PatchMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> updateInvoice(@PathVariable Long id, @Valid @RequestBody InvoiceDTO dto) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }

        try {
            Invoice invoice = billingInvoiceService.updateInvoice(id, dto);
            return ResponseEntity.ok(ApiResponse.success("Invoice berhasil diperbarui!", billingInvoiceService.getInvoiceRowById(invoice.getId())));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<ApiResponse<Void>> deleteInvoice(@PathVariable Long id) {
        if (!hasPermission("DELETE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }

        try {
            billingInvoiceService.deleteInvoice(id);
            return ResponseEntity.ok(ApiResponse.success("Invoice berhasil dihapus", null));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/{id:\\d+}/cancel")
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> cancelInvoice(@PathVariable Long id) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }

        try {
            Invoice invoice = billingInvoiceService.cancelInvoice(id);
            return ResponseEntity.ok(ApiResponse.success("Invoice berhasil dibatalkan!", billingInvoiceService.getInvoiceRowById(invoice.getId())));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/{id:\\d+}/payments")
    public ResponseEntity<ApiResponse<List<PaymentRecordDTO>>> getInvoicePayments(@PathVariable Long id) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        return ResponseEntity.ok(ApiResponse.success("Riwayat pembayaran invoice berhasil diambil", paymentManagementService.getPaymentsByInvoice(id)));
    }

    @PostMapping("/{id:\\d+}/payments")
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> recordPayment(@PathVariable Long id, @Valid @RequestBody RecordPaymentRequest request) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }

        try {
            return ResponseEntity.ok(ApiResponse.success("Pembayaran berhasil dicatat!", paymentManagementService.recordPayment(id, request)));
        } catch (UnexpectedRollbackException ex) {
            return resolvePaidInvoiceAfterRollbackOnly(id, ex);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/{id:\\d+}/pay")
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> payInvoice(@PathVariable Long id, @Valid @RequestBody QuickPayInvoiceRequest request) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }

        try {
            return ResponseEntity.ok(ApiResponse.success("Pembayaran invoice berhasil diproses", billingQuickActionService.payInvoice(id, request)));
        } catch (UnexpectedRollbackException ex) {
            return resolvePaidInvoiceAfterRollbackOnly(id, ex);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @DeleteMapping("/{id:\\d+}/payments")
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> deleteInvoicePayments(@PathVariable Long id) {
        if (!hasPermission("DELETE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }

        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Data pembayaran invoice berhasil dihapus",
                    paymentManagementService.deletePaymentsByInvoice(id)
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/{id:\\d+}/payments/delete")
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> deleteInvoicePaymentsViaPost(@PathVariable Long id) {
        if (!hasPermission("DELETE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }

        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Data pembayaran invoice berhasil dihapus",
                    paymentManagementService.deletePaymentsByInvoice(id)
            ));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    private ResponseEntity<ApiResponse<InvoiceRowDTO>> resolvePaidInvoiceAfterRollbackOnly(Long invoiceId, UnexpectedRollbackException ex) {
        try {
            InvoiceRowDTO invoice = billingInvoiceService.getInvoiceRowById(invoiceId);
            if (invoice != null && "paid".equalsIgnoreCase(invoice.getStatus())) {
                return ResponseEntity.ok(ApiResponse.success("Pembayaran invoice berhasil diproses", invoice));
            }
        } catch (Exception ignored) {
            // Fall back to the original rollback-only error when invoice refresh also fails.
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
    }

    private boolean hasPermission(String permission) {
        return switch (permission) {
            case "CREATE", "READ" -> TenantRoleAccess.canRead(SecurityContextHolder.getContext().getAuthentication());
            case "UPDATE" -> TenantRoleAccess.canWrite(SecurityContextHolder.getContext().getAuthentication());
            case "DELETE" -> TenantRoleAccess.canDelete(SecurityContextHolder.getContext().getAuthentication());
            default -> false;
        };
    }

    private boolean invoiceMatchesPeriod(InvoiceRowDTO invoice, Integer year, Integer month) {
        if (invoice == null) {
            return false;
        }
        if (year == null && month == null) {
            return true;
        }

        LocalDate referenceDate = resolveInvoiceReferenceDate(invoice);
        if (referenceDate == null) {
            return false;
        }
        if (year != null && referenceDate.getYear() != year) {
            return false;
        }
        return month == null || referenceDate.getMonthValue() == month;
    }

    private boolean invoiceMatchesDate(InvoiceRowDTO invoice, Integer dueDay, Integer year, Integer month) {
        if (invoice == null) {
            return false;
        }
        if (!invoiceMatchesPeriod(invoice, year, month)) {
            return false;
        }
        if (dueDay == null) {
            return true;
        }
        LocalDate dueDate = invoice.getDueDate();
        return dueDate != null && dueDate.getDayOfMonth() == dueDay;
    }

    private Integer parseDueDay(String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 1 && parsed <= 31 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean invoiceMatchesStatus(InvoiceRowDTO invoice, String status) {
        if (invoice == null) {
            return false;
        }
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        return resolveInvoiceStatus(invoice).equals(normalizeStatus(status));
    }

    private boolean invoiceMatchesSearch(InvoiceRowDTO invoice, String searchKeyword) {
        if (invoice == null) {
            return false;
        }
        if (searchKeyword == null || searchKeyword.isBlank()) {
            return true;
        }

        String keyword = normalizeSearchKeyword(searchKeyword);
        return normalizeSearchKeyword(invoice.getInvoiceNumber()).contains(keyword)
                || normalizeSearchKeyword(invoice.getCustomerName()).contains(keyword)
                || normalizeSearchKeyword(invoice.getCustomerCode()).contains(keyword)
                || normalizeSearchKeyword(invoice.getPaymentMethod()).contains(keyword)
                || normalizeSearchKeyword(invoice.getInvoiceTypeLabel()).contains(keyword);
    }

    private String normalizeSearchKeyword(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    private LocalDate resolveInvoiceReferenceDate(InvoiceRowDTO invoice) {
        if (invoice == null) {
            return null;
        }
        String resolvedStatus = resolveInvoiceStatus(invoice);
        if ("paid".equals(resolvedStatus)) {
            if (invoice.getPaymentDate() != null) {
                return invoice.getPaymentDate();
            }
            if (invoice.getBillingMonth() != null) {
                return invoice.getBillingMonth();
            }
            return invoice.getDueDate();
        }
        if (invoice.getBillingMonth() != null) {
            return invoice.getBillingMonth();
        }
        if (invoice.getDueDate() != null) {
            return invoice.getDueDate();
        }
        return invoice.getPaymentDate();
    }

    private String resolveInvoiceStatus(InvoiceRowDTO invoice) {
        if (invoice == null) {
            return "unpaid";
        }

        String normalizedStatus = normalizeStatus(invoice.getStatus());
        if ("paid".equals(normalizedStatus) || "cancelled".equals(normalizedStatus)) {
            return normalizedStatus;
        }
        if ("no_payment".equals(normalizedStatus)) {
            return "no_payment";
        }

        if (invoice.getDueDate() != null && invoice.getDueDate().isBefore(LocalDate.now())) {
            return "overdue";
        }

        return "overdue".equals(normalizedStatus) ? "overdue" : "unpaid";
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT)
                .trim()
                .replace('-', ' ')
                .replace('_', ' ');

        if ("paid".equals(normalized) || "lunas".equals(normalized) || "sudah bayar".equals(normalized)) {
            return "paid";
        }
        if ("cancelled".equals(normalized) || "dibatalkan".equals(normalized) || "batal".equals(normalized)) {
            return "cancelled";
        }
        if ("no payment".equals(normalized) || "tidak bayar".equals(normalized)) {
            return "no_payment";
        }
        if ("overdue".equals(normalized) || "jatuh tempo".equals(normalized)) {
            return "overdue";
        }
        if ("partial".equals(normalized)
                || "pending".equals(normalized)
                || "unpaid".equals(normalized)
                || "belum bayar".equals(normalized)
                || "belum lunas".equals(normalized)) {
            return "unpaid";
        }
        return "unpaid";
    }

}
