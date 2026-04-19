package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.DashboardHomeViewDTO;
import com.netmaster.nmx.dto.HistoryPaymentRowDTO;
import com.netmaster.nmx.dto.InvoiceRowDTO;
import com.netmaster.nmx.dto.ActivityLogRowDTO;
import com.netmaster.nmx.dto.ErrorLogRowDTO;
import com.netmaster.nmx.model.Role;
import com.netmaster.nmx.model.User;
import com.netmaster.nmx.repository.RoleRepository;
import com.netmaster.nmx.security.TenantRoleAccess;
import com.netmaster.nmx.service.AppLogService;
import com.netmaster.nmx.service.BillingInvoiceService;
import com.netmaster.nmx.service.DashboardHomeService;
import com.netmaster.nmx.service.ICustomerService;
import com.netmaster.nmx.service.PaymentManagementService;
import com.netmaster.nmx.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final UserService userService;
    private final RoleRepository roleRepository;
    private final ICustomerService customerService;
    private final BillingInvoiceService billingInvoiceService;
    private final PaymentManagementService paymentManagementService;
    private final AppLogService appLogService;
    private final DashboardHomeService dashboardHomeService;

    private String getCurrentUserPermissionLevel() {
        return TenantRoleAccess.permissionLevel(SecurityContextHolder.getContext().getAuthentication());
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()))) {
            return "redirect:/superadmin/dashboard";
        }
        return "redirect:/dashboard/home";
    }

    @GetMapping("/dashboard/home")
    public String dashboardHome(Model model) {
        DashboardHomeViewDTO dashboard = dashboardHomeService.buildDashboard();
        model.addAttribute("dashboard", dashboard);
        model.addAttribute("page", "dashboard-home");
        return "layout/base";
    }

    // Monitoring
    @GetMapping("/monitoring/trafik")
    public String monitoringTrafik(Model model) {
        model.addAttribute("page", "trafik");
        return "layout/base";
    }

    @GetMapping("/monitoring/alert")
    public String monitoringAlert(Model model) {
        model.addAttribute("page", "alert");
        return "layout/base";
    }

    @GetMapping("/pelanggan/history")
    public String pelangganHistory(Model model) {
        LocalDate now = LocalDate.now();
        List<HistoryPaymentRowDTO> historyRows = paymentManagementService.getCustomerHistoryRows("ALL");
        model.addAttribute("historyRows", historyRows);
        model.addAttribute("historyTotalCustomers", historyRows.size());
        model.addAttribute("historyTotalPaid", historyRows.stream()
                .filter(row -> "paid".equalsIgnoreCase(row.getPaymentStatus()))
                .count());
        model.addAttribute("historyTotalPending", historyRows.stream()
                .filter(row -> "unpaid".equalsIgnoreCase(row.getPaymentStatus())
                        || "partial".equalsIgnoreCase(row.getPaymentStatus()))
                .count());
        model.addAttribute("historyTotalOverdue", historyRows.stream()
                .filter(row -> "overdue".equalsIgnoreCase(row.getPaymentStatus()))
                .count());
        model.addAttribute("page", "history-payment");
        return "layout/base";
    }

    // Finance
    @GetMapping("/finance/invoice")
    public String financeInvoice(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String dueDay,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Model model
    ) {
        LocalDate now = LocalDate.now();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();
        Integer selectedYear = year != null ? year : currentYear;
        Integer selectedMonth = month != null ? month : currentMonth;
        String selectedDueDayValue = dueDay != null && !dueDay.isBlank() ? dueDay.trim() : String.valueOf(now.getDayOfMonth());
        Integer selectedDueDay = parseDueDay(selectedDueDayValue);
        String selectedStatus = status != null ? status.trim() : "all";
        String selectedSearch = normalizeInvoiceSearchKeyword(search);
        final Integer activeYear = selectedYear;
        final Integer activeMonth = selectedMonth;
        final Integer activeDueDay = selectedDueDay;

        List<InvoiceRowDTO> allInvoiceRows = billingInvoiceService.getAllInvoiceRows().stream()
                .sorted(Comparator.comparing((InvoiceRowDTO row) ->
                                row.getPaymentDate() != null ? row.getPaymentDate()
                                        : (row.getBillingMonth() != null ? row.getBillingMonth() : row.getDueDate()),
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        List<InvoiceRowDTO> invoiceRows = allInvoiceRows.stream()
                .filter(row -> invoiceMatchesDate(row, activeDueDay, activeYear, activeMonth))
                .filter(row -> invoiceMatchesStatus(row, selectedStatus, now))
                .filter(row -> invoiceMatchesSearch(row, selectedSearch))
                .toList();
        String currentInvoiceYear = selectedYear != null ? String.valueOf(selectedYear) : "";
        String currentInvoiceMonth = selectedMonth != null ? String.valueOf(selectedMonth) : "";
        List<String> invoiceYears = new ArrayList<>();
        invoiceYears.add(currentInvoiceYear);
        billingInvoiceService.getDistinctInvoiceYears().stream()
                .map(String::valueOf)
                .filter(availableYear -> !invoiceYears.contains(availableYear))
                .forEach(invoiceYears::add);
        List<String> invoiceMonths = allInvoiceRows.stream()
                .map(row -> resolveInvoiceReferenceDate(row, now))
                .filter(Objects::nonNull)
                .filter(referenceDate -> activeYear != null && referenceDate.getYear() == activeYear)
                .map(referenceDate -> String.valueOf(referenceDate.getMonthValue()))
                .distinct()
                .sorted(Comparator.comparingInt(Integer::parseInt))
                .toList();
        List<String> invoiceStatuses = allInvoiceRows.stream()
                .map(row -> resolveEffectiveInvoiceStatus(row, now))
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(availableStatus -> !availableStatus.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .toList();
        model.addAttribute("invoiceRows", invoiceRows);
        model.addAttribute("invoiceAllRows", allInvoiceRows);
        model.addAttribute("invoiceYears", invoiceYears);
        model.addAttribute("currentInvoiceYear", currentInvoiceYear);
        model.addAttribute("invoiceMonths", invoiceMonths);
        model.addAttribute("currentInvoiceMonth", currentInvoiceMonth);
        model.addAttribute("currentInvoiceDueDay", selectedDueDayValue);
        model.addAttribute("currentInvoiceStatus", selectedStatus);
        model.addAttribute("currentInvoiceSearch", selectedSearch);
        model.addAttribute("invoiceStatuses", invoiceStatuses);
        model.addAttribute("invoiceTotalCount", invoiceRows.size());
        model.addAttribute("invoicePendingCount", countUniqueCustomersByInvoiceStatus(invoiceRows, "unpaid", now));
        model.addAttribute("invoicePaidCount", countUniqueCustomersByInvoiceStatus(invoiceRows, "paid", now));
        model.addAttribute("invoiceOverdueCount", countUniqueCustomersByInvoiceStatus(invoiceRows, "overdue", now));
        model.addAttribute("page", "invoice");
        return "layout/base";
    }

    @GetMapping("/finance/pembayaran")
    public String financePembayaran(Model model) {
        model.addAttribute("page", "pembayaran");
        return "layout/base";
    }

    @GetMapping("/finance/piutang")
    public String financePiutang(Model model) {
        model.addAttribute("page", "piutang");
        return "layout/base";
    }

    @GetMapping("/finance/cashflow")
    public String financeCashflow(Model model) {
        model.addAttribute("page", "cashflow");
        return "layout/base";
    }

    @GetMapping("/finance/laporan")
    public String financeLaporan(Model model) {
        model.addAttribute("page", "laporan-keuangan");
        return "layout/base";
    }

    // Network
    @GetMapping("/network/mikrotik")
    public String networkMikrotik(Model model) {
        model.addAttribute("page", "mikrotik");
        return "layout/base";
    }

    @GetMapping("/network/server")
    public String networkServer(Model model) {
        model.addAttribute("permissionLevel", getCurrentUserPermissionLevel());
        model.addAttribute("page", "network-server");
        return "layout/base";
    }

    @GetMapping("/network/olt")
    public String networkOLT(Model model) {
        return "redirect:/network/server";
    }

    @GetMapping("/network/acs")
    public String networkACS(Model model) {
        model.addAttribute("page", "acs");
        return "layout/base";
    }

    @GetMapping("/network/vpn")
    public String networkVPN(Model model) {
        model.addAttribute("page", "vpn");
        return "layout/base";
    }

    @GetMapping("/network/pool")
    public String networkPool(Model model) {
        model.addAttribute("page", "ip-pool");
        return "layout/base";
    }

    // Inventory disabled
    @GetMapping({
            "/inventory/odp",
            "/inventory/server",
            "/inventory/ont",
            "/inventory/kabel",
            "/inventory/stok",
            "/inventory/data",
            "/inventory/laporan"
    })
    public String inventoryDisabled() {
        return "redirect:/dashboard";
    }

    // Other Modules
    @GetMapping("/reports")
    public String reports(Model model) {
        model.addAttribute("page", "reports");
        return "layout/base";
    }

    @GetMapping("/user")
    public String userManagement(Model model) {
        model.addAttribute("permissionLevel", getCurrentUserPermissionLevel());
        populateUserManagementModel(model);
        model.addAttribute("page", "user-mgmt");
        return "layout/base";
    }

    @GetMapping("/setting/user")
    public String settingUser(Model model) {
        model.addAttribute("permissionLevel", getCurrentUserPermissionLevel());
        populateUserManagementModel(model);
        model.addAttribute("page", "user-mgmt");
        return "layout/base";
    }

    @GetMapping("/setting/server")
    public String settingServer(Model model) {
        model.addAttribute("permissionLevel", getCurrentUserPermissionLevel());
        model.addAttribute("page", "network-server");
        return "layout/base";
    }

    @GetMapping("/automation")
    public String automation(Model model) {
        model.addAttribute("page", "automation");
        return "layout/base";
    }

    @GetMapping("/system")
    public String system(Model model) {
        model.addAttribute("page", "system");
        return "layout/base";
    }

    @GetMapping("/system/log-proses")
    public String processLog(Model model) {
        List<ActivityLogRowDTO> processLogRows = appLogService.getRecentActivityRows(250);
        populateProcessLogModel(model, processLogRows);
        model.addAttribute("page", "process-log");
        return "layout/base";
    }

    @GetMapping("/system/log-error")
    public String errorLog(Model model) {
        List<ErrorLogRowDTO> errorLogRows = appLogService.getRecentErrorRows(250);
        populateErrorLogModel(model, errorLogRows);
        model.addAttribute("page", "error-log");
        return "layout/base";
    }

    @GetMapping("/crm")
    public String crm(Model model) {
        model.addAttribute("page", "crm");
        return "layout/base";
    }

    @GetMapping("/mapping")
    public String mapping(Model model) {
        model.addAttribute("page", "mapping");
        return "layout/base";
    }

    @GetMapping("/company")
    public String company(Model model) {
        model.addAttribute("permissionLevel", getCurrentUserPermissionLevel());
        model.addAttribute("page", "company");
        return "layout/base";
    }

    @GetMapping("/setting/company")
    public String settingCompany(Model model) {
        model.addAttribute("permissionLevel", getCurrentUserPermissionLevel());
        model.addAttribute("page", "company");
        return "layout/base";
    }

    private void populateUserManagementModel(Model model) {
        List<User> users = userService.getAllUsers();
        List<Role> roles = roleRepository.findAll();

        long superAdminCount = users.stream()
                .filter(user -> user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(role -> "ROLE_SUPER_ADMIN".equals(role.getName())
                                || "ROLE_TENANT_SUPER_ADMIN".equals(role.getName())
                                || "ROLE_TENANT_ADMIN".equals(role.getName())))
                .count();
        long adminCount = users.stream()
                .filter(user -> user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(role -> "ROLE_ADMIN".equals(role.getName())
                                || "ROLE_STAFF".equals(role.getName())))
                .count();
        long activeCount = users.stream().filter(User::isActive).count();

        model.addAttribute("users", users);
        model.addAttribute("roles", roles);
        model.addAttribute("userCount", users.size());
        model.addAttribute("superAdminCount", superAdminCount);
        model.addAttribute("adminCount", adminCount);
        model.addAttribute("activeUserCount", activeCount);
    }

    private boolean invoiceMatchesPeriod(InvoiceRowDTO invoice, Integer year, Integer month) {
        if (invoice == null) {
            return false;
        }
        if (year == null && month == null) {
            return true;
        }

        LocalDate referenceDate = resolveInvoiceReferenceDate(invoice, LocalDate.now());
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

    private boolean invoiceMatchesStatus(InvoiceRowDTO invoice, String status, LocalDate today) {
        if (invoice == null) {
            return false;
        }
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }
        return normalizeInvoiceStatus(status).equals(resolveEffectiveInvoiceStatus(invoice, today));
    }

    private boolean invoiceMatchesSearch(InvoiceRowDTO invoice, String searchKeyword) {
        if (invoice == null) {
            return false;
        }
        if (searchKeyword == null || searchKeyword.isBlank()) {
            return true;
        }

        String keyword = normalizeInvoiceSearchKeyword(searchKeyword);
        return normalizeInvoiceSearchKeyword(invoice.getInvoiceNumber()).contains(keyword)
                || normalizeInvoiceSearchKeyword(invoice.getCustomerName()).contains(keyword)
                || normalizeInvoiceSearchKeyword(invoice.getCustomerCode()).contains(keyword)
                || normalizeInvoiceSearchKeyword(invoice.getPaymentMethod()).contains(keyword)
                || normalizeInvoiceSearchKeyword(invoice.getInvoiceTypeLabel()).contains(keyword);
    }

    private String normalizeInvoiceSearchKeyword(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }

    private long countUniqueCustomersByInvoiceStatus(List<InvoiceRowDTO> invoiceRows, String targetStatus, LocalDate today) {
        return invoiceRows.stream()
                .filter(Objects::nonNull)
                .filter(invoice -> targetStatus.equals(resolveEffectiveInvoiceStatus(invoice, today)))
                .map(this::resolveInvoiceCustomerKey)
                .filter(key -> !key.isBlank())
                .distinct()
                .count();
    }

    private String resolveInvoiceCustomerKey(InvoiceRowDTO invoice) {
        if (invoice == null) {
            return "";
        }
        if (invoice.getCustomerId() != null && invoice.getCustomerId() > 0) {
            return "customer:" + invoice.getCustomerId();
        }
        String customerName = invoice.getCustomerName() != null ? invoice.getCustomerName().trim().toLowerCase() : "";
        return customerName.isBlank() ? "" : "name:" + customerName;
    }

    private String resolveEffectiveInvoiceStatus(InvoiceRowDTO invoice, LocalDate today) {
        if (invoice == null) {
            return "unpaid";
        }

        String normalizedStatus = normalizeInvoiceStatus(invoice.getStatus());
        if ("paid".equals(normalizedStatus)) {
            return "paid";
        }
        if ("cancelled".equals(normalizedStatus)) {
            return "cancelled";
        }
        if ("no_payment".equals(normalizedStatus)) {
            return "no_payment";
        }

        LocalDate dueDate = invoice.getDueDate();
        if (dueDate != null && dueDate.isBefore(today)) {
            return "overdue";
        }

        return "unpaid";
    }

    private LocalDate resolveInvoiceReferenceDate(InvoiceRowDTO invoice, LocalDate today) {
        if (invoice == null) {
            return null;
        }

        String status = resolveEffectiveInvoiceStatus(invoice, today);
        if ("paid".equals(status)) {
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

    private String normalizeInvoiceStatus(String status) {
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
        if ("pending".equals(normalized)
                || "partial".equals(normalized)
                || "unpaid".equals(normalized)
                || "belum bayar".equals(normalized)
                || "belum lunas".equals(normalized)) {
            return "unpaid";
        }
        return "unpaid";
    }

    private void populateProcessLogModel(Model model, List<ActivityLogRowDTO> rows) {
        model.addAttribute("processLogRows", rows);
        model.addAttribute("processLogTotal", rows.size());
        model.addAttribute("processLogPageViews", rows.stream()
                .filter(row -> "PAGE_VIEW".equalsIgnoreCase(row.getActionType()))
                .count());
        model.addAttribute("processLogUserActions", rows.stream()
                .filter(row -> "USER_ACTION".equalsIgnoreCase(row.getActionType()))
                .count());
        model.addAttribute("processLogErrorCount", rows.stream()
                .filter(row -> "error".equalsIgnoreCase(row.getStatus()) || "warning".equalsIgnoreCase(row.getStatus()))
                .count());
    }

    private void populateErrorLogModel(Model model, List<ErrorLogRowDTO> rows) {
        model.addAttribute("errorLogRows", rows);
        model.addAttribute("errorLogTotal", rows.size());
        model.addAttribute("errorLog4xx", rows.stream()
                .filter(row -> row.getStatusCode() != null && row.getStatusCode() >= 400 && row.getStatusCode() < 500)
                .count());
        model.addAttribute("errorLog5xx", rows.stream()
                .filter(row -> row.getStatusCode() != null && row.getStatusCode() >= 500)
                .count());
        model.addAttribute("errorLogKnownUsers", rows.stream()
                .map(ErrorLogRowDTO::getUsername)
                .filter(username -> username != null && !username.isBlank() && !"anonymous".equalsIgnoreCase(username))
                .distinct()
                .count());
    }

}

