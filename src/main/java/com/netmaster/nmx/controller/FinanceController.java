package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.InvoiceDTO;
import com.netmaster.nmx.dto.InvoiceRowDTO;
import com.netmaster.nmx.model.Invoice;
import com.netmaster.nmx.service.BillingQuickActionService;
import com.netmaster.nmx.service.ICustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final ICustomerService customerService;
    private final BillingQuickActionService billingQuickActionService;

    // ==================== INVOICES ====================

    // Get all invoices
    @GetMapping("/invoices")
    public ResponseEntity<ApiResponse<List<InvoiceRowDTO>>> getAllInvoices(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String status) {
        List<InvoiceRowDTO> invoices = customerService.getAllInvoiceRows().stream()
                .filter(invoice -> invoiceMatchesPeriod(invoice, year, month))
                .filter(invoice -> invoiceMatchesStatus(invoice, status))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Data invoice berhasil diambil", invoices));
    }

    // Get invoice by ID
    @GetMapping("/invoices/{id}")
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> getInvoice(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Invoice ditemukan", customerService.getInvoiceRowById(id)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Create new invoice
    @PostMapping("/invoices")
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> createInvoice(@RequestBody InvoiceDTO dto) {
        try {
            Invoice invoice = customerService.createInvoice(dto);
            return ResponseEntity.ok(ApiResponse.success("Invoice berhasil dibuat", customerService.getInvoiceRowById(invoice.getId())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Update invoice
    @PutMapping("/invoices/{id}")
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> updateInvoice(@PathVariable Long id, @RequestBody InvoiceDTO dto) {
        try {
            Invoice updated = customerService.updateInvoice(id, dto);
            return ResponseEntity.ok(ApiResponse.success("Invoice berhasil diperbarui", customerService.getInvoiceRowById(updated.getId())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Delete invoice
    @DeleteMapping("/invoices/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteInvoice(@PathVariable Long id) {
        try {
            customerService.deleteInvoice(id);
            return ResponseEntity.ok(ApiResponse.success("Invoice berhasil dihapus", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Pay invoice
    @PostMapping("/invoices/{id}/pay")
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> payInvoice(@PathVariable Long id, @RequestBody Map<String, Object> paymentData) {
        try {
            BigDecimal amount = new BigDecimal(paymentData.get("amount").toString());
            String paymentMethod = (String) paymentData.get("paymentMethod");
            String notes = (String) paymentData.getOrDefault("notes", "");
            
            Invoice invoice = customerService.payInvoice(id, amount, paymentMethod, notes);
            return ResponseEntity.ok(ApiResponse.success("Pembayaran berhasil", customerService.getInvoiceRowById(invoice.getId())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Cancel invoice
    @PostMapping("/invoices/{id}/cancel")
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> cancelInvoice(@PathVariable Long id) {
        try {
            Invoice invoice = customerService.cancelInvoice(id);
            return ResponseEntity.ok(ApiResponse.success("Invoice berhasil dibatalkan", customerService.getInvoiceRowById(invoice.getId())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Get invoices by status
    @GetMapping("/invoices/status/{status}")
    public ResponseEntity<ApiResponse<List<InvoiceRowDTO>>> getInvoicesByStatus(
            @PathVariable String status,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        List<InvoiceRowDTO> invoices = customerService.getAllInvoiceRows().stream()
                .filter(invoice -> invoiceMatchesPeriod(invoice, year, month))
                .filter(invoice -> status.equalsIgnoreCase(invoice.getStatus()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Data invoice berhasil diambil", invoices));
    }

    // Get unpaid invoices
    @GetMapping("/invoices/unpaid")
    public ResponseEntity<ApiResponse<List<InvoiceRowDTO>>> getUnpaidInvoices(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        List<InvoiceRowDTO> invoices = customerService.getUnpaidInvoiceRows().stream()
                .filter(invoice -> invoiceMatchesPeriod(invoice, year, month))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Data invoice unpaid berhasil diambil", invoices));
    }

    // Get paid invoices
    @GetMapping("/invoices/paid")
    public ResponseEntity<ApiResponse<List<InvoiceRowDTO>>> getPaidInvoices(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        List<InvoiceRowDTO> invoices = customerService.getAllInvoiceRows().stream()
                .filter(invoice -> invoiceMatchesPeriod(invoice, year, month))
                .filter(invoice -> "paid".equalsIgnoreCase(invoice.getStatus()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Data invoice paid berhasil diambil", invoices));
    }

    // ==================== FINANCE STATISTICS ====================

    // Get finance statistics
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getStats(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String status) {
        LocalDate now = LocalDate.now();
        int selectedYear = year != null ? year : now.getYear();
        int selectedMonth = month != null ? month : now.getMonthValue();

        List<InvoiceRowDTO> all = customerService.getAllInvoiceRows().stream()
                .filter(invoice -> invoiceMatchesPeriod(invoice, selectedYear, selectedMonth))
                .filter(invoice -> invoiceMatchesStatus(invoice, status))
                .toList();
        
        BigDecimal totalRevenue = all.stream()
                .filter(inv -> "paid".equals(inv.getStatus()))
                .map(InvoiceRowDTO::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal pendingAmount = all.stream()
                .filter(inv -> "pending".equals(inv.getStatus()) || "overdue".equals(inv.getStatus()))
                .map(inv -> inv.getOutstandingAmount() != null ? inv.getOutstandingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long totalInvoices = all.size();
        long paidInvoices = all.stream().filter(inv -> "paid".equals(inv.getStatus())).count();
        long unpaidInvoices = all.stream().filter(inv -> "pending".equals(inv.getStatus()) || "overdue".equals(inv.getStatus())).count();

        return ResponseEntity.ok(ApiResponse.success("Statistik finance", Map.of(
                "year", selectedYear,
                "month", selectedMonth,
                "totalRevenue", totalRevenue,
                "pendingAmount", pendingAmount,
                "totalInvoices", totalInvoices,
                "paidInvoices", paidInvoices,
                "unpaidInvoices", unpaidInvoices
        )));
    }

    // Get cashflow based on actual invoice payments and outstanding balances
    @GetMapping("/cashflow")
    public ResponseEntity<ApiResponse<Object>> getCashflow(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int selectedYear = year != null ? year : now.getYear();
        Integer selectedMonth = month;
        Locale locale = new Locale("id", "ID");

        List<InvoiceRowDTO> allInvoices = customerService.getAllInvoiceRows();

        List<InvoiceRowDTO> paidInvoices = allInvoices.stream()
                .filter(invoice -> "paid".equalsIgnoreCase(invoice.getStatus()))
                .filter(invoice -> invoiceMatchesPaymentPeriod(invoice, selectedYear, selectedMonth))
                .toList();

        List<InvoiceRowDTO> periodInvoices = allInvoices.stream()
                .filter(invoice -> invoiceMatchesPeriod(invoice, Integer.valueOf(selectedYear), selectedMonth))
                .toList();

        BigDecimal cashIn = paidInvoices.stream()
                .map(invoice -> invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstandingAmount = periodInvoices.stream()
                .filter(invoice -> !isInvoiceSettled(invoice))
                .map(this::resolveOutstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal activationCashIn = paidInvoices.stream()
                .filter(invoice -> "activation".equalsIgnoreCase(invoice.getInvoiceType()))
                .map(invoice -> invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal subscriptionCashIn = paidInvoices.stream()
                .filter(invoice -> !"activation".equalsIgnoreCase(invoice.getInvoiceType()))
                .map(invoice -> invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal collectionRate = outstandingAmount.signum() == 0 && cashIn.signum() == 0
                ? BigDecimal.ZERO
                : cashIn.multiply(BigDecimal.valueOf(100))
                        .divide(cashIn.add(outstandingAmount), 2, java.math.RoundingMode.HALF_UP);

        List<Map<String, Object>> monthlySeries = new ArrayList<>();
        for (int monthIndex = 1; monthIndex <= 12; monthIndex++) {
            final int seriesMonth = monthIndex;

            BigDecimal monthlyCashIn = allInvoices.stream()
                    .filter(invoice -> "paid".equalsIgnoreCase(invoice.getStatus()))
                    .filter(invoice -> invoiceMatchesPaymentPeriod(invoice, selectedYear, seriesMonth))
                    .map(invoice -> invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal monthlyOutstanding = allInvoices.stream()
                    .filter(invoice -> invoiceMatchesPeriod(invoice, selectedYear, seriesMonth))
                    .filter(invoice -> !isInvoiceSettled(invoice))
                    .map(this::resolveOutstandingAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            long paymentCount = allInvoices.stream()
                    .filter(invoice -> "paid".equalsIgnoreCase(invoice.getStatus()))
                    .filter(invoice -> invoiceMatchesPaymentPeriod(invoice, selectedYear, seriesMonth))
                    .count();

            monthlySeries.add(Map.of(
                    "month", monthIndex,
                    "label", Month.of(monthIndex).getDisplayName(TextStyle.SHORT, locale),
                    "cashIn", monthlyCashIn,
                    "outstanding", monthlyOutstanding,
                    "paymentCount", paymentCount
            ));
        }

        List<Map<String, Object>> recentPayments = paidInvoices.stream()
                .sorted((left, right) -> {
                    LocalDate leftDate = left.getPaymentDate() != null ? left.getPaymentDate() : left.getBillingMonth();
                    LocalDate rightDate = right.getPaymentDate() != null ? right.getPaymentDate() : right.getBillingMonth();
                    if (leftDate == null && rightDate == null) {
                        return 0;
                    }
                    if (leftDate == null) {
                        return 1;
                    }
                    if (rightDate == null) {
                        return -1;
                    }
                    return rightDate.compareTo(leftDate);
                })
                .limit(8)
                .map(invoice -> {
                    Map<String, Object> payment = new LinkedHashMap<>();
                    payment.put("invoiceNumber", invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "-");
                    payment.put("customerName", invoice.getCustomerName() != null ? invoice.getCustomerName() : "-");
                    payment.put("invoiceTypeLabel", invoice.getInvoiceTypeLabel() != null ? invoice.getInvoiceTypeLabel() : "Pembayaran Langganan Bulanan");
                    payment.put("paymentDate", invoice.getPaymentDate());
                    payment.put("amountPaid", invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO);
                    payment.put("paymentMethod", invoice.getPaymentMethod() != null ? invoice.getPaymentMethod() : "-");
                    return payment;
                })
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("year", selectedYear);
        payload.put("month", selectedMonth);
        payload.put("cashIn", cashIn);
        payload.put("outstandingAmount", outstandingAmount);
        payload.put("activationCashIn", activationCashIn);
        payload.put("subscriptionCashIn", subscriptionCashIn);
        payload.put("collectionRate", collectionRate);
        payload.put("paidInvoiceCount", paidInvoices.size());
        payload.put("outstandingInvoiceCount", periodInvoices.stream().filter(invoice -> !isInvoiceSettled(invoice)).count());
        payload.put("monthlySeries", monthlySeries);
        payload.put("recentPayments", recentPayments);

        return ResponseEntity.ok(ApiResponse.success("Cashflow", payload));
    }

    // Get receivables (piutang)
    @GetMapping("/receivables")
    public ResponseEntity<ApiResponse<Object>> getReceivables() {
        List<InvoiceRowDTO> unpaid = customerService.getUnpaidInvoiceRows();
        
        BigDecimal totalReceivables = unpaid.stream()
                .map(inv -> inv.getOutstandingAmount() != null ? inv.getOutstandingAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        long overdueCount = unpaid.stream()
                .filter(inv -> "overdue".equals(inv.getStatus()))
                .count();

        return ResponseEntity.ok(ApiResponse.success("Receivables", Map.of(
                "totalReceivables", totalReceivables,
                "unpaidCount", unpaid.size(),
                "overdueCount", overdueCount
        )));
    }

    // ==================== BILLING ====================

    // Generate monthly invoices for all active customers
    @PostMapping("/billing/generate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateMonthlyInvoices() {
        int generated = billingQuickActionService.generateMonthlyInvoices();
        return ResponseEntity.ok(ApiResponse.success("Monthly billing generated", Map.of(
                "generated", generated,
                "message", generated > 0
                        ? "Billing generation completed"
                        : "Tidak ada invoice baru yang perlu dibuat"
        )));
    }

    private boolean invoiceMatchesPeriod(InvoiceRowDTO invoice, Integer year, Integer month) {
        if (invoice == null) {
            return false;
        }
        if (year == null && month == null) {
            return true;
        }

        LocalDate referenceDate = invoice.getBillingMonth() != null ? invoice.getBillingMonth() : invoice.getDueDate();
        if (referenceDate == null) {
            return false;
        }
        if (year != null && referenceDate.getYear() != year) {
            return false;
        }
        return month == null || referenceDate.getMonthValue() == month;
    }

    private boolean invoiceMatchesPeriod(InvoiceRowDTO invoice, int year, int month) {
        return invoiceMatchesPeriod(invoice, Integer.valueOf(year), Integer.valueOf(month));
    }

    private boolean invoiceMatchesStatus(InvoiceRowDTO invoice, String status) {
        if (invoice == null) {
            return false;
        }
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        String invoiceStatus = invoice.getStatus() != null ? invoice.getStatus().toLowerCase(Locale.ROOT) : "";
        String normalizedStatus = status.toLowerCase(Locale.ROOT);
        if ("unpaid".equals(normalizedStatus) || "belum-bayar".equals(normalizedStatus)) {
            return "pending".equals(invoiceStatus) || "partial".equals(invoiceStatus);
        }
        return normalizedStatus.equals(invoiceStatus);
    }

    private boolean invoiceMatchesPaymentPeriod(InvoiceRowDTO invoice, Integer year, Integer month) {
        if (invoice == null) {
            return false;
        }

        LocalDate paymentDate = invoice.getPaymentDate();
        if (paymentDate == null) {
            return false;
        }
        if (year != null && paymentDate.getYear() != year) {
            return false;
        }
        return month == null || paymentDate.getMonthValue() == month;
    }

    private boolean isInvoiceSettled(InvoiceRowDTO invoice) {
        if (invoice == null) {
            return true;
        }
        String status = invoice.getStatus() != null ? invoice.getStatus().toLowerCase(Locale.ROOT) : "";
        return "paid".equals(status) || "cancelled".equals(status);
    }

    private BigDecimal resolveOutstandingAmount(InvoiceRowDTO invoice) {
        if (invoice == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal explicitOutstanding = invoice.getOutstandingAmount();
        if (explicitOutstanding != null && explicitOutstanding.signum() > 0) {
            return explicitOutstanding;
        }

        BigDecimal totalAmount = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal amountPaid = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal remaining = totalAmount.subtract(amountPaid);
        return remaining.signum() > 0 ? remaining : BigDecimal.ZERO;
    }
}

