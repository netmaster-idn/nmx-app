package com.netmaster.nmx.service;

import com.netmaster.nmx.dto.DashboardHomeViewDTO;
import com.netmaster.nmx.dto.InvoiceRowDTO;
import com.netmaster.nmx.model.AcsDevice;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.model.NetworkAlert;
import com.netmaster.nmx.model.NetworkDevice;
import com.netmaster.nmx.model.Ticket;
import com.netmaster.nmx.repository.AcsDeviceRepository;
import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import com.netmaster.nmx.repository.NetworkAlertRepository;
import com.netmaster.nmx.repository.NetworkDeviceRepository;
import com.netmaster.nmx.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DashboardHomeService {

    private static final Locale ID_LOCALE = Locale.forLanguageTag("id-ID");
    private static final double LOW_SIGNAL_THRESHOLD = -25.0d;

    private final CustomerRepository customerRepository;
    private final CustomerServiceEntityRepository customerServiceEntityRepository;
    private final BillingInvoiceService billingInvoiceService;
    private final TicketRepository ticketRepository;
    private final NetworkAlertRepository networkAlertRepository;
    private final NetworkDeviceRepository networkDeviceRepository;
    private final AcsDeviceRepository acsDeviceRepository;

    public DashboardHomeViewDTO buildDashboard() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        List<InvoiceRowDTO> invoices = billingInvoiceService.getAllInvoiceRows();
        List<Ticket> tickets = ticketRepository.findAll();
        List<NetworkAlert> activeAlerts = networkAlertRepository.findActiveAlerts();
        List<NetworkDevice> activeDevices = networkDeviceRepository.findByIsActiveTrue();
        List<AcsDevice> activeOnts = acsDeviceRepository.findByIsActiveTrue();
        List<CustomerServiceEntity> mappedServices = customerServiceEntityRepository.findAllForMapping();

        DashboardHomeViewDTO view = new DashboardHomeViewDTO();
        view.setCustomerSummary(buildCustomerSummary());
        view.setRevenueSummary(buildRevenueSummary(invoices, today));
        view.setDeviceSummary(buildDeviceSummary(activeDevices));
        view.setOntSummary(buildOntSummary(activeOnts));
        view.setTicketSummary(buildTicketSummary(tickets, today));
        view.setHealthSummary(buildHealthSummary(activeDevices, view.getCustomerSummary()));
        view.setAlerts(buildAlerts(activeAlerts, activeDevices, invoices));
        view.setRevenueLabels(buildRevenueLabels(today));
        view.setRevenueValues(buildRevenueValues(invoices, today));
        view.setHealthLabels(List.of("Online", "Warning", "Offline", "Maintenance"));
        view.setHealthValues(buildHealthValues(activeDevices));
        view.setMapPoints(buildMapPoints(mappedServices));
        view.setMapSummary(buildMapSummary(mappedServices));
        view.setRegistrations(buildRegistrations());
        view.setHighlightSummary(buildHighlightSummary(invoices, activeDevices, today));
        return view;
    }

    private DashboardHomeViewDTO.CustomerSummary buildCustomerSummary() {
        List<com.netmaster.nmx.model.Customer> customers = customerRepository.findAll();
        long active = customers.stream().filter(customer -> hasStatus(customer.getStatus(), "active")).count();
        long suspended = customers.stream().filter(customer -> hasStatus(customer.getStatus(), "suspended")).count();
        long inactive = customers.stream().filter(customer -> isInactiveStatus(customer.getStatus())).count();
        return new DashboardHomeViewDTO.CustomerSummary(customers.size(), active, suspended, inactive);
    }

    private DashboardHomeViewDTO.RevenueSummary buildRevenueSummary(List<InvoiceRowDTO> invoices, LocalDate today) {
        LocalDate monthStart = today.withDayOfMonth(1);
        BigDecimal currentMonthRevenue = invoices.stream()
                .filter(this::isPaidInvoice)
                .filter(invoice -> invoice.getPaymentDate() != null)
                .filter(invoice -> !invoice.getPaymentDate().isBefore(monthStart) && !invoice.getPaymentDate().isAfter(today))
                .map(this::amountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long monthInvoices = invoices.stream()
                .filter(invoice -> invoice.getBillingMonth() != null)
                .filter(invoice -> invoice.getBillingMonth().getYear() == today.getYear())
                .filter(invoice -> invoice.getBillingMonth().getMonthValue() == today.getMonthValue())
                .count();

        long overdueCount = invoices.stream().filter(invoice -> isOverdue(invoice, today)).count();

        BigDecimal todayPaid = invoices.stream()
                .filter(this::isPaidInvoice)
                .filter(invoice -> today.equals(invoice.getPaymentDate()))
                .map(this::amountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outstanding = invoices.stream()
                .filter(invoice -> !isSettled(invoice))
                .map(this::outstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String subtitle = "Revenue " + today.getMonth().getDisplayName(TextStyle.FULL, ID_LOCALE) + " " + today.getYear();
        return new DashboardHomeViewDTO.RevenueSummary(
                formatCurrencyCompact(currentMonthRevenue),
                subtitle,
                monthInvoices,
                overdueCount,
                formatCurrencyCompact(todayPaid),
                formatCurrencyCompact(outstanding)
        );
    }

    private DashboardHomeViewDTO.DeviceSummary buildDeviceSummary(List<NetworkDevice> devices) {
        long online = devices.stream().filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.ONLINE).count();
        long offline = devices.stream().filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.OFFLINE).count();
        long warning = devices.stream().filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.WARNING).count();
        long maintenance = devices.stream().filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.MAINTENANCE).count();
        return new DashboardHomeViewDTO.DeviceSummary(devices.size(), online, offline, warning, maintenance);
    }

    private DashboardHomeViewDTO.OntSummary buildOntSummary(List<AcsDevice> onts) {
        long online = onts.stream().filter(device -> hasStatus(device.getStatus(), "online")).count();
        long offline = onts.stream().filter(device -> hasStatus(device.getStatus(), "offline")).count();
        long lowSignal = onts.stream()
                .filter(device -> device.getOpticalRxPower() != null && device.getOpticalRxPower() <= LOW_SIGNAL_THRESHOLD)
                .count();
        long dyingGasp = onts.stream().filter(device -> hasStatus(device.getStatus(), "dying_gasp")).count();
        return new DashboardHomeViewDTO.OntSummary(onts.size(), online, offline, lowSignal, dyingGasp);
    }

    private DashboardHomeViewDTO.TicketSummary buildTicketSummary(List<Ticket> tickets, LocalDate today) {
        long active = tickets.stream().filter(ticket -> isOneOf(ticket.getStatus(), "open", "in_progress", "pending")).count();
        long highPriority = tickets.stream().filter(ticket -> isOneOf(ticket.getPriority(), "high", "critical")).count();
        long pending = tickets.stream().filter(ticket -> hasStatus(ticket.getStatus(), "pending")).count();
        long resolvedToday = tickets.stream()
                .filter(ticket -> ticket.getResolvedAt() != null)
                .filter(ticket -> ticket.getResolvedAt().toLocalDate().equals(today))
                .count();
        return new DashboardHomeViewDTO.TicketSummary(active, highPriority, pending, resolvedToday);
    }

    private DashboardHomeViewDTO.HealthSummary buildHealthSummary(
            List<NetworkDevice> devices,
            DashboardHomeViewDTO.CustomerSummary customerSummary
    ) {
        long totalDevices = Math.max(devices.size(), 1);
        long healthyDevices = devices.stream().filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.ONLINE).count();
        BigDecimal serviceHealth = percentage(healthyDevices, totalDevices);

        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate previousMonth = currentMonth.minusMonths(1);
        long currentRegistrations = customerRepository.findAll().stream()
                .filter(customer -> customer.getRegistrationDate() != null)
                .filter(customer -> customer.getRegistrationDate().getYear() == currentMonth.getYear())
                .filter(customer -> customer.getRegistrationDate().getMonthValue() == currentMonth.getMonthValue())
                .count();
        long previousRegistrations = customerRepository.findAll().stream()
                .filter(customer -> customer.getRegistrationDate() != null)
                .filter(customer -> customer.getRegistrationDate().getYear() == previousMonth.getYear())
                .filter(customer -> customer.getRegistrationDate().getMonthValue() == previousMonth.getMonthValue())
                .count();

        BigDecimal growthRate;
        if (previousRegistrations == 0) {
            growthRate = currentRegistrations == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        } else {
            growthRate = BigDecimal.valueOf(currentRegistrations - previousRegistrations)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(previousRegistrations), 1, RoundingMode.HALF_UP);
        }

        BigDecimal inactiveRate = percentage(customerSummary.getInactive() + customerSummary.getSuspended(),
                Math.max(customerSummary.getTotal(), 1));

        return new DashboardHomeViewDTO.HealthSummary(
                formatPercent(serviceHealth),
                signedPercent(growthRate),
                formatPercent(inactiveRate)
        );
    }

    private List<DashboardHomeViewDTO.AlertItem> buildAlerts(
            List<NetworkAlert> activeAlerts,
            List<NetworkDevice> devices,
            List<InvoiceRowDTO> invoices
    ) {
        List<DashboardHomeViewDTO.AlertItem> items = new ArrayList<>();

        long criticalCount = activeAlerts.stream().filter(alert -> isOneOf(alert.getSeverity(), "critical", "major")).count();
        String criticalTitle = criticalCount > 0 ? criticalCount + " alert kritis aktif" : "Tidak ada alert kritis";
        String criticalDetail = activeAlerts.stream()
                .filter(alert -> isOneOf(alert.getSeverity(), "critical", "major"))
                .map(NetworkAlert::getMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("Sistem monitoring tidak mendeteksi gangguan kritis saat ini.");
        items.add(new DashboardHomeViewDTO.AlertItem("Critical Alert", "critical", "fas fa-exclamation-triangle",
                criticalTitle, criticalDetail));

        long deviceIssues = devices.stream()
                .filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.OFFLINE
                        || device.getStatus() == NetworkDevice.DeviceStatus.WARNING)
                .count();
        items.add(new DashboardHomeViewDTO.AlertItem(
                "Network Alert",
                deviceIssues > 0 ? "warning" : "info",
                "fas fa-network-wired",
                deviceIssues + " device butuh perhatian",
                devices.stream()
                        .filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.OFFLINE
                                || device.getStatus() == NetworkDevice.DeviceStatus.WARNING)
                        .map(device -> safeText(device.getDeviceName()))
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse("Semua device aktif berada pada kondisi normal.")
        ));

        long overdueCount = invoices.stream().filter(invoice -> isOverdue(invoice, LocalDate.now())).count();
        BigDecimal overdueAmount = invoices.stream()
                .filter(invoice -> isOverdue(invoice, LocalDate.now()))
                .map(this::outstandingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        items.add(new DashboardHomeViewDTO.AlertItem(
                "Billing Alert",
                overdueCount > 0 ? "billing" : "info",
                "fas fa-file-invoice-dollar",
                overdueCount + " invoice overdue",
                overdueCount > 0 ? "Outstanding " + formatCurrencyCompact(overdueAmount) : "Tidak ada invoice overdue."
        ));

        long unresolvedCount = activeAlerts.stream().filter(alert -> !hasStatus(alert.getStatus(), "closed")).count();
        items.add(new DashboardHomeViewDTO.AlertItem(
                "System Alert",
                unresolvedCount > 0 ? "info" : "success",
                "fas fa-server",
                unresolvedCount > 0 ? unresolvedCount + " issue sedang dipantau" : "Sistem monitoring stabil",
                activeAlerts.stream()
                        .sorted(Comparator.comparing(NetworkAlert::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .map(NetworkAlert::getDeviceName)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .map(name -> "Terakhir terdeteksi pada " + name)
                        .orElse("Belum ada issue aktif dari sumber monitoring.")
        ));

        return items;
    }

    private List<String> buildRevenueLabels(LocalDate today) {
        List<String> labels = new ArrayList<>();
        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            labels.add(date.getDayOfMonth() + " " + date.getMonth().getDisplayName(TextStyle.SHORT, ID_LOCALE));
        }
        return labels;
    }

    private List<Long> buildRevenueValues(List<InvoiceRowDTO> invoices, LocalDate today) {
        Map<LocalDate, BigDecimal> totalsByDay = new LinkedHashMap<>();
        for (int offset = 6; offset >= 0; offset--) {
            totalsByDay.put(today.minusDays(offset), BigDecimal.ZERO);
        }

        invoices.stream()
                .filter(this::isPaidInvoice)
                .filter(invoice -> invoice.getPaymentDate() != null)
                .filter(invoice -> !invoice.getPaymentDate().isBefore(today.minusDays(6)) && !invoice.getPaymentDate().isAfter(today))
                .forEach(invoice -> totalsByDay.computeIfPresent(
                        invoice.getPaymentDate(),
                        (date, total) -> total.add(amountPaid(invoice))
                ));

        return totalsByDay.values().stream()
                .map(BigDecimal::longValue)
                .toList();
    }

    private List<Long> buildHealthValues(List<NetworkDevice> devices) {
        long online = devices.stream().filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.ONLINE).count();
        long warning = devices.stream().filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.WARNING).count();
        long offline = devices.stream().filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.OFFLINE).count();
        long maintenance = devices.stream().filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.MAINTENANCE).count();
        return List.of(online, warning, offline, maintenance);
    }

    private List<DashboardHomeViewDTO.MapPoint> buildMapPoints(List<CustomerServiceEntity> services) {
        List<CustomerServiceEntity> mapped = services.stream()
                .filter(service -> service.getCustomer() != null)
                .filter(service -> service.getCustomer().getLatitude() != null && service.getCustomer().getLongitude() != null)
                .limit(24)
                .toList();

        if (mapped.isEmpty()) {
            return List.of();
        }

        double minLat = mapped.stream().map(service -> service.getCustomer().getLatitude().doubleValue()).min(Double::compareTo).orElse(0d);
        double maxLat = mapped.stream().map(service -> service.getCustomer().getLatitude().doubleValue()).max(Double::compareTo).orElse(0d);
        double minLon = mapped.stream().map(service -> service.getCustomer().getLongitude().doubleValue()).min(Double::compareTo).orElse(0d);
        double maxLon = mapped.stream().map(service -> service.getCustomer().getLongitude().doubleValue()).max(Double::compareTo).orElse(0d);

        double latRange = Math.max(maxLat - minLat, 0.0001d);
        double lonRange = Math.max(maxLon - minLon, 0.0001d);

        List<DashboardHomeViewDTO.MapPoint> points = new ArrayList<>();
        for (CustomerServiceEntity service : mapped) {
            double lat = service.getCustomer().getLatitude().doubleValue();
            double lon = service.getCustomer().getLongitude().doubleValue();
            double top = 12d + ((maxLat - lat) / latRange) * 76d;
            double left = 10d + ((lon - minLon) / lonRange) * 80d;
            points.add(new DashboardHomeViewDTO.MapPoint(
                    String.format(Locale.US, "%.2f%%", top),
                    String.format(Locale.US, "%.2f%%", left),
                    mapStatusColor(service.getCustomer().getStatus()),
                    service.getCustomer().getFullName()
            ));
        }
        return points;
    }

    private DashboardHomeViewDTO.MapSummary buildMapSummary(List<CustomerServiceEntity> services) {
        long active = services.stream().filter(service -> service.getCustomer() != null && hasStatus(service.getCustomer().getStatus(), "active")).count();
        long suspended = services.stream().filter(service -> service.getCustomer() != null && hasStatus(service.getCustomer().getStatus(), "suspended")).count();
        long inactive = services.stream().filter(service -> service.getCustomer() != null && isInactiveStatus(service.getCustomer().getStatus())).count();
        return new DashboardHomeViewDTO.MapSummary(active, suspended, inactive);
    }

    private List<DashboardHomeViewDTO.RegistrationItem> buildRegistrations() {
        List<com.netmaster.nmx.model.Customer> customers = customerRepository.findAll();
        LocalDate current = LocalDate.now().withDayOfMonth(1);
        Map<LocalDate, Long> totals = new LinkedHashMap<>();
        for (int offset = 3; offset >= 0; offset--) {
            LocalDate month = current.minusMonths(offset);
            totals.put(month, 0L);
        }

        customers.stream()
                .filter(customer -> customer.getRegistrationDate() != null)
                .forEach(customer -> {
                    LocalDate month = customer.getRegistrationDate().withDayOfMonth(1);
                    if (totals.containsKey(month)) {
                        totals.put(month, totals.get(month) + 1);
                    }
                });

        long max = totals.values().stream().mapToLong(Long::longValue).max().orElse(1L);
        List<DashboardHomeViewDTO.RegistrationItem> items = new ArrayList<>();
        totals.forEach((month, count) -> items.add(new DashboardHomeViewDTO.RegistrationItem(
                month.getMonth().getDisplayName(TextStyle.FULL, ID_LOCALE),
                count,
                (int) Math.max(8, Math.round((double) count * 100d / Math.max(max, 1L))),
                month.equals(current)
        )));
        return items;
    }

    private DashboardHomeViewDTO.HighlightSummary buildHighlightSummary(
            List<InvoiceRowDTO> invoices,
            List<NetworkDevice> devices,
            LocalDate today
    ) {
        long totalMonthInvoices = invoices.stream()
                .filter(invoice -> invoice.getBillingMonth() != null)
                .filter(invoice -> invoice.getBillingMonth().getYear() == today.getYear())
                .filter(invoice -> invoice.getBillingMonth().getMonthValue() == today.getMonthValue())
                .count();
        long paidMonthInvoices = invoices.stream()
                .filter(this::isPaidInvoice)
                .filter(invoice -> invoice.getBillingMonth() != null)
                .filter(invoice -> invoice.getBillingMonth().getYear() == today.getYear())
                .filter(invoice -> invoice.getBillingMonth().getMonthValue() == today.getMonthValue())
                .count();

        BigDecimal collectionRate = percentage(paidMonthInvoices, Math.max(totalMonthInvoices, 1L));
        long issueDevices = devices.stream()
                .filter(device -> device.getStatus() == NetworkDevice.DeviceStatus.OFFLINE
                        || device.getStatus() == NetworkDevice.DeviceStatus.WARNING)
                .count();

        return new DashboardHomeViewDTO.HighlightSummary(
                "Collection Rate",
                formatPercent(collectionRate),
                issueDevices > 0 ? issueDevices + " device masih perlu tindak lanjut" : "Tidak ada device issue aktif"
        );
    }

    private boolean isPaidInvoice(InvoiceRowDTO invoice) {
        return invoice != null && hasStatus(invoice.getStatus(), "paid");
    }

    private boolean isSettled(InvoiceRowDTO invoice) {
        return invoice != null && isOneOf(invoice.getStatus(), "paid", "cancelled");
    }

    private boolean isOverdue(InvoiceRowDTO invoice, LocalDate today) {
        return invoice != null
                && !isSettled(invoice)
                && invoice.getDueDate() != null
                && invoice.getDueDate().isBefore(today);
    }

    private BigDecimal amountPaid(InvoiceRowDTO invoice) {
        return invoice != null && invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
    }

    private BigDecimal outstandingAmount(InvoiceRowDTO invoice) {
        return invoice != null && invoice.getOutstandingAmount() != null ? invoice.getOutstandingAmount() : BigDecimal.ZERO;
    }

    private String formatCurrencyCompact(BigDecimal amount) {
        BigDecimal value = amount != null ? amount : BigDecimal.ZERO;
        BigDecimal billion = BigDecimal.valueOf(1_000_000_000L);
        BigDecimal million = BigDecimal.valueOf(1_000_000L);
        BigDecimal thousand = BigDecimal.valueOf(1_000L);

        if (value.abs().compareTo(billion) >= 0) {
            return "Rp " + trimTrailingZeros(value.divide(billion, 1, RoundingMode.HALF_UP)) + "B";
        }
        if (value.abs().compareTo(million) >= 0) {
            return "Rp " + trimTrailingZeros(value.divide(million, 1, RoundingMode.HALF_UP)) + "M";
        }
        if (value.abs().compareTo(thousand) >= 0) {
            return "Rp " + trimTrailingZeros(value.divide(thousand, 1, RoundingMode.HALF_UP)) + "K";
        }
        return "Rp " + NumberFormat.getNumberInstance(ID_LOCALE).format(value);
    }

    private String trimTrailingZeros(BigDecimal value) {
        BigDecimal normalized = value.stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0).toPlainString() : normalized.toPlainString();
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP);
    }

    private String formatPercent(BigDecimal value) {
        BigDecimal safeValue = value != null ? value : BigDecimal.ZERO;
        return trimTrailingZeros(safeValue) + "%";
    }

    private String signedPercent(BigDecimal value) {
        BigDecimal safeValue = value != null ? value : BigDecimal.ZERO;
        String prefix = safeValue.signum() > 0 ? "+" : "";
        return prefix + formatPercent(safeValue);
    }

    private String mapStatusColor(String status) {
        if (hasStatus(status, "active")) {
            return "#22c55e";
        }
        if (hasStatus(status, "suspended")) {
            return "#f59e0b";
        }
        return "#ef4444";
    }

    private boolean hasStatus(String value, String expected) {
        return value != null && expected != null && value.trim().equalsIgnoreCase(expected);
    }

    private boolean isOneOf(String value, String... candidates) {
        if (value == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (value.trim().equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInactiveStatus(String status) {
        return isOneOf(status, "inactive", "nonaktif", "terminated");
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
