package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.InvoiceRowDTO;
import com.netmaster.nmx.model.*;
import com.netmaster.nmx.repository.*;
import com.netmaster.nmx.service.ICustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportsController {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final TicketRepository ticketRepository;
    private final MikrotikDeviceRepository mikrotikRepository;
    private final OltDeviceRepository oltRepository;
    private final ICustomerService customerService;
    @Value("${nmx.inventory.enabled:false}")
    private boolean inventoryEnabled;

    // ==================== DASHBOARD SUMMARY ====================

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDashboardSummary() {
        Map<String, Object> summary = new HashMap<>();

        // Customer stats
        List<Customer> customers = customerRepository.findAll();
        long totalCustomers = customers.size();
        long activeCustomers = customers.stream().filter(c -> "active".equals(c.getStatus())).count();
        long suspendedCustomers = customers.stream().filter(c -> "suspended".equals(c.getStatus())).count();

        summary.put("customers", Map.of(
                "total", totalCustomers,
                "active", activeCustomers,
                "suspended", suspendedCustomers
        ));

        // Financial stats
        List<InvoiceRowDTO> invoices = customerService.getAllInvoiceRows();
        BigDecimal totalRevenue = invoices.stream()
                .filter(inv -> !"cancelled".equals(inv.getStatus()))
                .map(inv -> inv.getAmountPaid() != null ? inv.getAmountPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingAmount = invoices.stream()
                .filter(inv -> !"paid".equals(inv.getStatus()) && !"cancelled".equals(inv.getStatus()))
                .map(inv -> {
                    BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
                    BigDecimal paid = inv.getAmountPaid() != null ? inv.getAmountPaid() : BigDecimal.ZERO;
                    BigDecimal outstanding = total.subtract(paid);
                    return outstanding.signum() > 0 ? outstanding : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        summary.put("finance", Map.of(
                "totalRevenue", totalRevenue,
                "pendingAmount", pendingAmount,
                "unpaidInvoices", invoices.stream().filter(inv -> "pending".equals(inv.getStatus())).count()
        ));

        // Ticket stats
        List<Ticket> tickets = ticketRepository.findAll();
        summary.put("tickets", Map.of(
                "total", tickets.size(),
                "open", tickets.stream().filter(t -> "open".equals(t.getStatus())).count(),
                "resolved", tickets.stream().filter(t -> "resolved".equals(t.getStatus())).count()
        ));

        // Network stats
        List<MikrotikDevice> mikrotiks = mikrotikRepository.findAll();
        List<OltDevice> olts = oltRepository.findAll();

        long mikrotikOnline = mikrotiks.stream().filter(d -> "online".equals(d.getStatus())).count();
        long mikrotikOffline = mikrotiks.stream().filter(d -> "offline".equals(d.getStatus())).count();
        long oltOnline = olts.stream().filter(d -> "online".equals(d.getStatus())).count();
        long oltOffline = olts.stream().filter(d -> "offline".equals(d.getStatus())).count();

        summary.put("network", Map.of(
                "mikrotikOnline", mikrotikOnline,
                "mikrotikOffline", mikrotikOffline,
                "oltOnline", oltOnline,
                "oltOffline", oltOffline,
                "devicesOnline", mikrotikOnline + oltOnline,
                "devicesOffline", mikrotikOffline + oltOffline,
                "devicesTotal", mikrotiks.size() + olts.size()
        ));

        // Inventory stats
        summary.put("inventory", Map.of(
                "enabled", inventoryEnabled,
                "totalItems", 0,
                "lowStock", 0
        ));

        return ResponseEntity.ok(ApiResponse.success("Dashboard summary", summary));
    }

    // ==================== CUSTOMER REPORTS ====================

    @GetMapping("/customers/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCustomerReport() {
        List<Customer> customers = customerRepository.findAll();

        Map<String, Object> report = new HashMap<>();
        report.put("totalCustomers", customers.size());
        report.put("activeCustomers", customers.stream().filter(c -> "active".equals(c.getStatus())).count());
        report.put("pendingCustomers", customers.stream().filter(c -> "pending".equals(c.getStatus())).count());
        report.put("suspendedCustomers", customers.stream().filter(c -> "suspended".equals(c.getStatus())).count());
        report.put("inactiveCustomers", customers.stream().filter(c -> "inactive".equals(c.getStatus())).count());

        return ResponseEntity.ok(ApiResponse.success("Customer report", report));
    }

    // ==================== FINANCIAL REPORTS ====================

    @GetMapping("/finance/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFinanceReport(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String status) {
        List<InvoiceRowDTO> invoices = customerService.getAllInvoiceRows();
        LocalDate today = LocalDate.now();
        int selectedYear = year != null ? year : today.getYear();
        int selectedMonth = month != null ? month : today.getMonthValue();

        List<InvoiceRowDTO> periodInvoices = invoices.stream()
                .filter(inv -> invoiceMatchesPeriod(inv, selectedYear, selectedMonth))
                .filter(inv -> invoiceMatchesStatus(inv, status))
                .toList();

        BigDecimal totalRevenue = periodInvoices.stream()
                .filter(inv -> !"cancelled".equals(inv.getStatus()))
                .map(inv -> inv.getAmountPaid() != null ? inv.getAmountPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pendingAmount = periodInvoices.stream()
                .filter(inv -> !"paid".equals(inv.getStatus()) && !"cancelled".equals(inv.getStatus()))
                .map(inv -> {
                    BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
                    BigDecimal paid = inv.getAmountPaid() != null ? inv.getAmountPaid() : BigDecimal.ZERO;
                    BigDecimal outstanding = total.subtract(paid);
                    return outstanding.signum() > 0 ? outstanding : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal overdueAmount = periodInvoices.stream()
                .filter(inv -> "overdue".equals(inv.getStatus()))
                .map(inv -> {
                    BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
                    BigDecimal paid = inv.getAmountPaid() != null ? inv.getAmountPaid() : BigDecimal.ZERO;
                    BigDecimal outstanding = total.subtract(paid);
                    return outstanding.signum() > 0 ? outstanding : BigDecimal.ZERO;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal paidThisMonth = periodInvoices.stream()
                .filter(inv -> inv.getPaymentDate() != null
                        && inv.getPaymentDate().getYear() == selectedYear
                        && inv.getPaymentDate().getMonthValue() == selectedMonth)
                .map(inv -> inv.getAmountPaid() != null ? inv.getAmountPaid() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> report = new HashMap<>();
        report.put("year", selectedYear);
        report.put("month", selectedMonth);
        report.put("totalInvoices", periodInvoices.size());
        report.put("paidInvoices", periodInvoices.stream().filter(inv -> "paid".equals(inv.getStatus())).count());
        report.put("pendingInvoices", periodInvoices.stream().filter(inv -> "pending".equals(inv.getStatus())).count());
        report.put("partialInvoices", periodInvoices.stream().filter(inv -> "partial".equals(inv.getStatus())).count());
        report.put("overdueInvoices", periodInvoices.stream().filter(inv -> "overdue".equals(inv.getStatus())).count());
        report.put("cancelledInvoices", periodInvoices.stream().filter(inv -> "cancelled".equals(inv.getStatus())).count());
        report.put("totalRevenue", totalRevenue);
        report.put("pendingAmount", pendingAmount);
        report.put("overdueAmount", overdueAmount);
        report.put("paidThisMonth", paidThisMonth);
        report.put("paymentTransactions", periodInvoices.stream().filter(inv -> inv.getPaymentDate() != null).count());

        return ResponseEntity.ok(ApiResponse.success("Finance report", report));
    }

    // ==================== NETWORK REPORTS ====================

    @GetMapping("/network/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNetworkReport() {
        List<MikrotikDevice> mikrotiks = mikrotikRepository.findAll();
        List<OltDevice> olts = oltRepository.findAll();
        long mikrotikOnline = mikrotiks.stream().filter(d -> "online".equals(d.getStatus())).count();
        long mikrotikOffline = mikrotiks.stream().filter(d -> "offline".equals(d.getStatus())).count();
        long oltOnline = olts.stream().filter(d -> "online".equals(d.getStatus())).count();
        long oltOffline = olts.stream().filter(d -> "offline".equals(d.getStatus())).count();

        Map<String, Object> report = new HashMap<>();
        report.put("mikrotik", Map.of(
                "total", mikrotiks.size(),
                "online", mikrotikOnline,
                "offline", mikrotikOffline
        ));
        report.put("olt", Map.of(
                "total", olts.size(),
                "online", oltOnline,
                "offline", oltOffline
        ));
        report.put("totals", Map.of(
                "total", mikrotiks.size() + olts.size(),
                "online", mikrotikOnline + oltOnline,
                "offline", mikrotikOffline + oltOffline
        ));

        return ResponseEntity.ok(ApiResponse.success("Network report", report));
    }

    // ==================== TICKET REPORTS ====================

    @GetMapping("/tickets/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTicketReport() {
        List<Ticket> tickets = ticketRepository.findAll();

        Map<String, Object> report = new HashMap<>();
        report.put("totalTickets", tickets.size());
        report.put("openTickets", tickets.stream().filter(t -> "open".equals(t.getStatus())).count());
        report.put("inProgressTickets", tickets.stream().filter(t -> "in_progress".equals(t.getStatus())).count());
        report.put("resolvedTickets", tickets.stream().filter(t -> "resolved".equals(t.getStatus())).count());
        report.put("closedTickets", tickets.stream().filter(t -> "closed".equals(t.getStatus())).count());
        
        // By priority
        report.put("critical", tickets.stream().filter(t -> "critical".equals(t.getPriority())).count());
        report.put("high", tickets.stream().filter(t -> "high".equals(t.getPriority())).count());
        report.put("medium", tickets.stream().filter(t -> "medium".equals(t.getPriority())).count());
        report.put("low", tickets.stream().filter(t -> "low".equals(t.getPriority())).count());

        return ResponseEntity.ok(ApiResponse.success("Ticket report", report));
    }

    private boolean invoiceMatchesPeriod(InvoiceRowDTO invoice, int year, int month) {
        if (invoice == null) {
            return false;
        }
        LocalDate referenceDate = invoice.getBillingMonth() != null ? invoice.getBillingMonth() : invoice.getDueDate();
        if (referenceDate == null) {
            return false;
        }
        return referenceDate.getYear() == year && referenceDate.getMonthValue() == month;
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
}

