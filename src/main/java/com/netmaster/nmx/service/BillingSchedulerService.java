package com.netmaster.nmx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netmaster.nmx.model.BillingAutomationSetting;
import com.netmaster.nmx.model.CompanySetting;
import com.netmaster.nmx.model.Customer;
import com.netmaster.nmx.model.CustomerBillingStatus;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.model.Invoice;
import com.netmaster.nmx.model.InvoiceDeliveryLog;
import com.netmaster.nmx.model.InternetPackage;
import com.netmaster.nmx.model.MikrotikDevice;
import com.netmaster.nmx.model.Payment;
import com.netmaster.nmx.model.SchedulerRunLog;
import com.netmaster.nmx.repository.CustomerBillingStatusRepository;
import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import com.netmaster.nmx.repository.CompanySettingRepository;
import com.netmaster.nmx.repository.InvoiceDeliveryLogRepository;
import com.netmaster.nmx.repository.InvoiceRepository;
import com.netmaster.nmx.repository.MikrotikDeviceRepository;
import com.netmaster.nmx.repository.PaymentRepository;
import com.netmaster.nmx.repository.PppoeActionLogRepository;
import com.netmaster.nmx.repository.SchedulerRunLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingSchedulerService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerServiceEntityRepository customerServiceRepository;
    private final CompanySettingRepository companySettingRepository;
    private final BillingAutomationSettingsService settingsService;
    private final InvoiceDeliveryLogRepository deliveryLogRepository;
    private final PppoeActionLogRepository pppoeActionLogRepository;
    private final CustomerBillingStatusRepository billingStatusRepository;
    private final SchedulerRunLogRepository schedulerRunLogRepository;
    private final BillingCustomerStatusProjectionService projectionService;
    private final BillingPppoeAutomationService pppoeAutomationService;
    private final BillingAuditTrailService billingAuditTrailService;
    private final BillingStatusSupport billingStatusSupport;
    private final BillingOperatorContextService operatorContextService;
    private final WhatsappGatewayService whatsappGatewayService;
    private final InvoiceDocumentService invoiceDocumentService;
    private final MikrotikDeviceRepository mikrotikDeviceRepository;
    private final MikrotikConnectionService mikrotikConnectionService;
    private final MikrotikRouterOsApiClient mikrotikRouterOsApiClient;
    private final CompanySettingsSchemaSupport companySettingsSchemaSupport;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public Map<String, Object> getSummary() {
        projectionService.refreshAll();
        List<Customer> customers = customerRepository.findAll();
        List<Customer> activeCustomers = customers.stream().filter(this::isCustomerOperationallyActive).toList();
        LocalDate today = LocalDate.now();
        List<Invoice> invoices = invoiceRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total_customers", activeCustomers.size());
        summary.put("upcoming_invoices_7_days", invoices.stream()
                .filter(invoice -> invoice.getCustomer() != null && isCustomerOperationallyActive(invoice.getCustomer()))
                .filter(invoice -> invoice.getDueDate() != null)
                .filter(invoice -> !invoice.getDueDate().isBefore(today) && !invoice.getDueDate().isAfter(today.plusDays(7)))
                .filter(invoice -> !"paid".equals(resolveInvoiceStatus(invoice)))
                .count());
        summary.put("invoices_sent", invoices.stream()
                .filter(invoice -> "sent".equals(resolveInvoiceStatus(invoice)) || "paid".equals(resolveInvoiceStatus(invoice)))
                .count());
        summary.put("receipts_sent", payments.stream().filter(payment -> payment.getReceiptSentAt() != null).count());
        summary.put("unpaid_customers", billingStatusRepository.findAll().stream()
                .filter(status -> !"paid".equalsIgnoreCase(status.getCurrentPaymentStatus()))
                .count());
        summary.put("overdue_customers", billingStatusRepository.findAll().stream()
                .filter(status -> "overdue".equalsIgnoreCase(status.getCurrentInvoiceStatus()))
                .count());
        summary.put("pppoe_disabled_count", customers.stream()
                .filter(customer -> "disabled".equals(billingStatusSupport.normalizePppoeStatus(customer.getPppoeStatus())))
                .count());
        summary.put("failed_whatsapp_count", safeInvoiceDeliveryLogs().stream()
                .filter(logEntry -> "failed".equalsIgnoreCase(logEntry.getStatus()))
                .count());
        summary.put("whatsapp_connection", resolveWhatsappConnection());
        summary.put("mikrotik_connection", resolveMikrotikConnection());
        summary.put("last_scheduler_run", schedulerRunLogRepository.findTopByOrderByStartedAtDesc().map(this::toSchedulerPayload).orElse(null));
        summary.put("automation_settings", settingsService.toPayload(settingsService.getOrCreate()));
        return summary;
    }

    @Transactional
    public List<Map<String, Object>> getUpcomingInvoices(int days) {
        projectionService.refreshAll();
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(Math.max(days, 0));
        return invoiceRepository.findAll().stream()
                .filter(invoice -> invoice.getCustomer() != null && isCustomerOperationallyActive(invoice.getCustomer()))
                .filter(invoice -> invoice.getDueDate() != null)
                .filter(invoice -> !invoice.getDueDate().isBefore(start) && !invoice.getDueDate().isAfter(end))
                .filter(invoice -> !"paid".equals(resolveInvoiceStatus(invoice)))
                .sorted(Comparator.comparing(Invoice::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::toCustomerRow)
                .toList();
    }

    @Transactional
    public Map<String, Object> getCustomerBillingList(Map<String, String> filters,
                                                      int page,
                                                      int size,
                                                      String sortBy,
                                                      String sortDirection) {
        projectionService.refreshAll();
        List<Map<String, Object>> rows = customerRepository.findAll().stream()
                .sorted(Comparator.comparing(customer -> safe(customer.getFullName()), String.CASE_INSENSITIVE_ORDER))
                .map(this::toCustomerRow)
                .filter(applyFilters(filters))
                .sorted(comparator(sortBy, sortDirection))
                .toList();

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int fromIndex = Math.min(safePage * safeSize, rows.size());
        int toIndex = Math.min(fromIndex + safeSize, rows.size());
        PageImpl<Map<String, Object>> result = new PageImpl<>(
                rows.subList(fromIndex, toIndex),
                PageRequest.of(safePage, safeSize),
                rows.size()
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", result.getContent());
        payload.put("page", result.getNumber());
        payload.put("size", result.getSize());
        payload.put("total_elements", result.getTotalElements());
        payload.put("total_pages", result.getTotalPages());
        payload.put("sort_by", sortBy);
        payload.put("sort_direction", sortDirection);
        return payload;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAutomationSettings() {
        return settingsService.toPayload(settingsService.getOrCreate());
    }

    @Transactional
    public Map<String, Object> updateAutomationSettings(com.netmaster.nmx.dto.BillingAutomationSettingsRequest request) {
        BillingAutomationSetting updated = settingsService.update(request);
        billingAuditTrailService.record("BILLING_AUTOMATION_UPDATE", "Pengaturan WhatsApp Billing Scheduler diperbarui");
        return settingsService.toPayload(updated);
    }

    @Transactional
    public Map<String, Object> sendInvoice(Long customerId, Long invoiceId, boolean force, String mode) {
        Invoice invoice = resolveInvoice(customerId, invoiceId);
        if (!force && invoice.getSentAt() != null && "success".equalsIgnoreCase(invoice.getWhatsappStatus())) {
            throw new IllegalStateException("Invoice sudah pernah dikirim. Gunakan resend atau force=true.");
        }

        Customer customer = invoice.getCustomer();
        String phone = billingStatusSupport.normalizePhone(customer);
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("Nomor WhatsApp pelanggan belum tersedia");
        }

        InvoiceDeliveryLog logEntry = new InvoiceDeliveryLog();
        logEntry.setCustomer(customer);
        logEntry.setInvoice(invoice);
        logEntry.setChannel("whatsapp");
        logEntry.setTarget(phone);
        logEntry.setMessageType("invoice");
        logEntry.setStatus("queued");
        logEntry.setSendMethod(mode);
        logEntry.setSentBy(resolveActor(mode));
        logEntry.setPayload(toJson(Map.of("invoiceId", invoice.getId(), "phone", phone, "mode", mode)));
        deliveryLogRepository.save(logEntry);

        try {
            String message = buildInvoiceMessage(invoice);
            Map<String, Object> gatewayResponse = whatsappGatewayService.sendMessage(
                    new com.netmaster.nmx.dto.WhatsappSendMessageRequest(phone, null, message)
            ).getData();

            LocalDateTime now = LocalDateTime.now();
            invoice.setSentAt(now);
            invoice.setSendMethod(mode);
            invoice.setWhatsappStatus("success");
            invoice.setLastReminderAt(now);
            invoice.setStatus("sent");
            invoiceRepository.save(invoice);

            logEntry.setStatus("success");
            logEntry.setProviderMessageId(stringValue(gatewayResponse, "messageId"));
            logEntry.setResponsePayload(toJson(gatewayResponse));
            logEntry.setSentAt(now);
            deliveryLogRepository.save(logEntry);
            billingAuditTrailService.record("BILLING_INVOICE_SEND", "Invoice " + invoice.getInvoiceNumber() + " dikirim via WhatsApp ke " + phone);
            projectionService.refreshCustomers(List.of(customer.getId()));

            return Map.of(
                    "customer_id", customer.getId(),
                    "invoice_id", invoice.getId(),
                    "invoice_number", invoice.getInvoiceNumber(),
                    "status", "success",
                    "provider_message_id", stringValue(gatewayResponse, "messageId"),
                    "sent_at", now
            );
        } catch (Exception ex) {
            invoice.setWhatsappStatus("failed");
            invoiceRepository.save(invoice);
            logEntry.setStatus("failed");
            logEntry.setErrorMessage(ex.getMessage());
            deliveryLogRepository.save(logEntry);
            projectionService.refreshCustomers(List.of(customer.getId()));
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    @Transactional
    public Map<String, Object> sendInvoiceBulk(Collection<Long> invoiceIds, boolean force) {
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        int failed = 0;
        for (Long invoiceId : invoiceIds) {
            try {
                Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new IllegalArgumentException("Invoice tidak ditemukan"));
                results.add(sendInvoice(invoice.getCustomer().getId(), invoiceId, force, "manual"));
                success++;
            } catch (Exception ex) {
                failed++;
                results.add(Map.of("invoice_id", invoiceId, "status", "failed", "message", ex.getMessage()));
            }
        }
        return Map.of("total", invoiceIds.size(), "success", success, "failed", failed, "results", results);
    }

    @Transactional
    public Map<String, Object> resendInvoice(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new IllegalArgumentException("Invoice tidak ditemukan"));
        return sendInvoice(invoice.getCustomer().getId(), invoiceId, true, "resend");
    }

    @Transactional
    public Map<String, Object> sendReceipt(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElseThrow(() -> new IllegalArgumentException("Pembayaran tidak ditemukan"));
        Invoice invoice = payment.getInvoice();
        if (invoice == null) {
            throw new IllegalArgumentException("Invoice pembayaran tidak ditemukan");
        }
        Customer customer = payment.getCustomer() != null ? payment.getCustomer() : invoice.getCustomer();
        String phone = billingStatusSupport.normalizePhone(customer);
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("Nomor WhatsApp pelanggan belum tersedia");
        }

        InvoiceDeliveryLog logEntry = null;
        if (tableExists("invoice_delivery_logs")) {
            logEntry = new InvoiceDeliveryLog();
            logEntry.setCustomer(customer);
            logEntry.setInvoice(invoice);
            logEntry.setChannel("whatsapp");
            logEntry.setTarget(phone);
            logEntry.setMessageType("receipt");
            logEntry.setStatus("queued");
            logEntry.setSendMethod("manual");
            logEntry.setSentBy(operatorContextService.currentActor());
            logEntry.setPayload(toJson(Map.of("paymentId", paymentId, "invoiceId", invoice.getId(), "phone", phone)));
            deliveryLogRepository.save(logEntry);
        }

        try {
            byte[] pdfBytes = invoiceDocumentService.renderInvoiceDocumentPdf(invoice.getId(), "80");
            String base64Data = Base64.getEncoder().encodeToString(pdfBytes);
            Map<String, Object> gatewayResponse = whatsappGatewayService.sendDocument(
                    new com.netmaster.nmx.dto.WhatsappSendDocumentRequest(
                            phone,
                            "application/pdf",
                            invoice.getInvoiceNumber() + "-receipt-80mm.pdf",
                            base64Data,
                            ""
                    )
            ).getData();

            LocalDateTime now = LocalDateTime.now();
            payment.setReceiptSentAt(now);
            payment.setReceiptSendMethod("whatsapp");
            payment.setStatus("paid");
            paymentRepository.save(payment);
            invoice.setPaidAt(invoice.getPaidAt() != null ? invoice.getPaidAt() : now);
            invoiceRepository.save(invoice);

            if (logEntry != null) {
                logEntry.setStatus("success");
                logEntry.setProviderMessageId(stringValue(gatewayResponse, "messageId"));
                logEntry.setResponsePayload(toJson(gatewayResponse));
                logEntry.setSentAt(now);
                deliveryLogRepository.save(logEntry);
            }
            billingAuditTrailService.record("BILLING_RECEIPT_SEND", "Struk pembayaran " + invoice.getInvoiceNumber() + " dikirim");
            projectionService.refreshCustomers(List.of(customer.getId()));

            return Map.of(
                    "payment_id", payment.getId(),
                    "invoice_id", invoice.getId(),
                    "status", "success",
                    "provider_message_id", stringValue(gatewayResponse, "messageId"),
                    "sent_at", now
            );
        } catch (Exception ex) {
            if (logEntry != null) {
                logEntry.setStatus("failed");
                logEntry.setErrorMessage(ex.getMessage());
                deliveryLogRepository.save(logEntry);
            }
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    @Transactional
    public Map<String, Object> disablePppoe(Long customerId, String reason, String executionMode) {
        Map<String, Object> result = pppoeAutomationService.disableCustomer(customerId, null, null, reason, executionMode);
        projectionService.refreshCustomers(List.of(customerId));
        return result;
    }

    @Transactional
    public Map<String, Object> enablePppoe(Long customerId, String reason, String executionMode) {
        Map<String, Object> result = pppoeAutomationService.enableCustomer(customerId, null, null, reason, executionMode);
        projectionService.refreshCustomers(List.of(customerId));
        return result;
    }

    @Transactional
    public Map<String, Object> autoDisableOverdueCustomers() {
        BillingAutomationSetting settings = settingsService.getOrCreate();
        int threshold = settings.getLatePaymentDisableDays();
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        int failed = 0;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) getCustomerBillingList(Map.of("invoice_status", "overdue"), 0, Integer.MAX_VALUE, "dueDate", "asc").get("content");
        for (Map<String, Object> row : content) {
            Integer overdueDays = integerValue(row.get("overdueDays"));
            Boolean eligible = Boolean.TRUE.equals(row.get("eligibleForDisable"));
            if (!eligible || overdueDays == null || overdueDays < threshold) {
                continue;
            }
            try {
                Long customerId = longValue(row.get("customerId"));
                results.add(pppoeAutomationService.disableCustomer(
                        customerId,
                        invoiceRepository.findById(longValue(row.get("invoiceId"))).orElse(null),
                        null,
                        "Auto disable overdue " + overdueDays + " hari",
                        "auto"
                ));
                success++;
            } catch (Exception ex) {
                failed++;
                results.add(Map.of("customerId", row.get("customerId"), "status", "failed", "message", ex.getMessage()));
            }
        }
        projectionService.refreshAll();
        return Map.of("success", success, "failed", failed, "results", results);
    }

    @Transactional
    public Map<String, Object> autoEnablePaidCustomers() {
        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        int failed = 0;
        for (CustomerBillingStatus status : billingStatusRepository.findAll()) {
            if (!"paid".equalsIgnoreCase(status.getCurrentPaymentStatus()) || !"disabled".equalsIgnoreCase(status.getPppoeCurrentState())) {
                continue;
            }
            try {
                Payment latestPayment = paymentRepository.findByCustomerIdOrderByPaymentDateDescIdDesc(status.getCustomer().getId()).stream().findFirst().orElse(null);
                results.add(pppoeAutomationService.enableCustomer(
                        status.getCustomer().getId(),
                        latestPayment != null ? latestPayment.getInvoice() : null,
                        latestPayment,
                        "Auto enable setelah pembayaran",
                        "auto"
                ));
                success++;
            } catch (Exception ex) {
                failed++;
                results.add(Map.of("customerId", status.getCustomer().getId(), "status", "failed", "message", ex.getMessage()));
            }
        }
        projectionService.refreshAll();
        return Map.of("success", success, "failed", failed, "results", results);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getInvoiceDeliveryLogs() {
        try {
            List<Map<String, Object>> rows = deliveryLogRepository.findTop200ByOrderByCreatedAtDesc().stream().map(logEntry -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", logEntry.getId());
                item.put("customerId", logEntry.getCustomer() != null ? logEntry.getCustomer().getId() : null);
                item.put("customerName", logEntry.getCustomer() != null ? logEntry.getCustomer().getFullName() : null);
                item.put("invoiceId", logEntry.getInvoice() != null ? logEntry.getInvoice().getId() : null);
                item.put("invoiceNumber", logEntry.getInvoice() != null ? logEntry.getInvoice().getInvoiceNumber() : null);
                item.put("channel", logEntry.getChannel());
                item.put("target", logEntry.getTarget());
                item.put("messageType", logEntry.getMessageType());
                item.put("status", logEntry.getStatus());
                item.put("providerMessageId", logEntry.getProviderMessageId());
                item.put("errorMessage", logEntry.getErrorMessage());
                item.put("sendMethod", logEntry.getSendMethod());
                item.put("sentAt", logEntry.getSentAt());
                item.put("sentBy", logEntry.getSentBy());
                item.put("createdAt", logEntry.getCreatedAt());
                return item;
            }).toList();
            return Map.of("items", rows, "total", rows.size());
        } catch (DataAccessException ex) {
            if (isMissingRelation(ex, "invoice_delivery_logs")) {
                log.warn("Invoice delivery log table is not available for the current tenant yet. Returning empty logs.");
                return Map.of("items", List.of(), "total", 0);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPppoeActionLogs() {
        try {
            List<Map<String, Object>> rows = pppoeActionLogRepository.findTop200ByOrderByCreatedAtDesc().stream().map(logEntry -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", logEntry.getId());
                item.put("customerId", logEntry.getCustomer() != null ? logEntry.getCustomer().getId() : null);
                item.put("customerName", logEntry.getCustomer() != null ? logEntry.getCustomer().getFullName() : null);
                item.put("invoiceId", logEntry.getInvoice() != null ? logEntry.getInvoice().getId() : null);
                item.put("paymentId", logEntry.getPayment() != null ? logEntry.getPayment().getId() : null);
                item.put("pppoeUsername", logEntry.getPppoeUsername());
                item.put("actionType", logEntry.getActionType());
                item.put("reason", logEntry.getReason());
                item.put("executionMode", logEntry.getExecutionMode());
                item.put("status", logEntry.getStatus());
                item.put("errorMessage", logEntry.getErrorMessage());
                item.put("executedBy", logEntry.getExecutedBy());
                item.put("executedAt", logEntry.getExecutedAt());
                item.put("createdAt", logEntry.getCreatedAt());
                return item;
            }).toList();
            return Map.of("items", rows, "total", rows.size());
        } catch (DataAccessException ex) {
            if (isMissingRelation(ex, "pppoe_action_logs")) {
                log.warn("PPPoE action log table is not available for the current tenant yet. Returning empty logs.");
                return Map.of("items", List.of(), "total", 0);
            }
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSchedulerRuns() {
        return schedulerRunLogRepository.findTop50ByOrderByStartedAtDesc().stream()
                .map(this::toSchedulerPayload)
                .toList();
    }

    private Invoice resolveInvoice(Long customerId, Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice tidak ditemukan"));
        if (invoice.getCustomer() == null || !Objects.equals(invoice.getCustomer().getId(), customerId)) {
            throw new IllegalArgumentException("Invoice tidak sesuai dengan pelanggan");
        }
        return invoice;
    }

    private Predicate<Map<String, Object>> applyFilters(Map<String, String> filters) {
        String search = normalize(filters.get("search"));
        String invoiceStatus = normalize(filters.get("invoice_status"));
        String paymentStatus = normalize(filters.get("payment_status"));
        String pppoeStatus = normalize(filters.get("pppoe_status"));
        String sendMethod = normalize(filters.get("send_method"));
        String area = normalize(filters.get("area"));
        String group = normalize(filters.get("group"));
        String packageName = normalize(filters.get("package"));
        LocalDate dueDateFrom = parseDate(filters.get("due_date_from"));
        LocalDate dueDateTo = parseDate(filters.get("due_date_to"));

        return row -> {
            if (StringUtils.hasText(search)) {
                String haystack = (safe(row.get("customerName")) + " " + safe(row.get("customerCode")) + " " + safe(row.get("phone"))).toLowerCase(Locale.ROOT);
                if (!haystack.contains(search)) {
                    return false;
                }
            }
            if (StringUtils.hasText(invoiceStatus) && !invoiceStatus.equals(normalize((String) row.get("invoiceStatus")))) return false;
            if (StringUtils.hasText(paymentStatus) && !paymentStatus.equals(normalize((String) row.get("paymentStatus")))) return false;
            if (StringUtils.hasText(pppoeStatus) && !pppoeStatus.equals(normalize((String) row.get("pppoeStatus")))) return false;
            if (StringUtils.hasText(sendMethod) && !sendMethod.equals(normalize((String) row.get("sendMethod")))) return false;
            if (StringUtils.hasText(area) && !safe(row.get("area")).toLowerCase(Locale.ROOT).contains(area)) return false;
            if (StringUtils.hasText(group) && !safe(row.get("group")).toLowerCase(Locale.ROOT).contains(group)) return false;
            if (StringUtils.hasText(packageName) && !safe(row.get("packageName")).toLowerCase(Locale.ROOT).contains(packageName)) return false;
            LocalDate dueDate = (LocalDate) row.get("dueDate");
            if (dueDateFrom != null && (dueDate == null || dueDate.isBefore(dueDateFrom))) return false;
            if (dueDateTo != null && (dueDate == null || dueDate.isAfter(dueDateTo))) return false;
            return true;
        };
    }

    private boolean isMissingRelation(Throwable throwable, String relationName) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                String relation = relationName.toLowerCase(Locale.ROOT);
                if ((normalized.contains("does not exist") || normalized.contains("doesn't exist"))
                        && normalized.contains(relation)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private List<InvoiceDeliveryLog> safeInvoiceDeliveryLogs() {
        if (!tableExists("invoice_delivery_logs")) {
            return List.of();
        }
        try {
            return deliveryLogRepository.findTop200ByOrderByCreatedAtDesc();
        } catch (DataAccessException ex) {
            if (isMissingRelation(ex, "invoice_delivery_logs")) {
                log.warn("Invoice delivery log table is not available for the current tenant yet. Returning empty delivery logs.");
                return List.of();
            }
            throw ex;
        }
    }

    private Optional<InvoiceDeliveryLog> safeFindDeliveryLog(Long invoiceId, String messageType) {
        if (!tableExists("invoice_delivery_logs")) {
            return Optional.empty();
        }
        try {
            return deliveryLogRepository.findTopByInvoiceIdAndMessageTypeOrderByCreatedAtDesc(invoiceId, messageType);
        } catch (DataAccessException ex) {
            if (isMissingRelation(ex, "invoice_delivery_logs")) {
                log.warn("Invoice delivery log table is not available for the current tenant yet. Skipping delivery log lookup.");
                return Optional.empty();
            }
            throw ex;
        }
    }

    private boolean tableExists(String tableName) {
        try {
            String relation = jdbcTemplate.queryForObject("select to_regclass(?)", String.class, tableName);
            return StringUtils.hasText(relation);
        } catch (DataAccessException ex) {
            log.warn("Unable to verify relation {} existence. Assuming unavailable for safety.", tableName, ex);
            return false;
        }
    }

    private Comparator<Map<String, Object>> comparator(String sortBy, String direction) {
        Comparator<Map<String, Object>> comparator = switch (normalize(sortBy)) {
            case "customercode" -> Comparator.comparing(row -> safe(row.get("customerCode")), String.CASE_INSENSITIVE_ORDER);
            case "amount" -> Comparator.comparing(row -> decimalValue(row.get("amount")), Comparator.nullsLast(Comparator.naturalOrder()));
            case "invoicestatus" -> Comparator.comparing(row -> safe(row.get("invoiceStatus")), String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing(row -> (LocalDate) row.get("dueDate"), Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "desc".equalsIgnoreCase(direction) ? comparator.reversed() : comparator;
    }

    private Map<String, Object> toCustomerRow(Invoice invoice) {
        return toCustomerRow(invoice.getCustomer());
    }

    private Map<String, Object> toCustomerRow(Customer customer) {
        CustomerServiceEntity service = customer != null && customer.getId() != null
                ? customerServiceRepository.findTopByCustomerIdOrderByCreatedAtDesc(customer.getId()).orElse(null)
                : null;
        List<Invoice> invoices = customer != null && customer.getId() != null
                ? invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customer.getId())
                : List.of();
        Invoice currentInvoice = selectCurrentInvoice(invoices);
        Payment latestPayment = currentInvoice != null
                ? paymentRepository.findByInvoiceIdOrderByPaymentDateDescIdDesc(currentInvoice.getId()).stream().findFirst().orElse(null)
                : customer != null && customer.getId() != null
                ? paymentRepository.findByCustomerIdOrderByPaymentDateDescIdDesc(customer.getId()).stream().findFirst().orElse(null)
                : null;
        InvoiceDeliveryLog invoiceLog = currentInvoice != null
                ? safeFindDeliveryLog(currentInvoice.getId(), "invoice").orElse(null)
                : null;
        InvoiceDeliveryLog receiptLog = currentInvoice != null
                ? safeFindDeliveryLog(currentInvoice.getId(), "receipt").orElse(null)
                : null;
        CustomerBillingStatus projection = customer != null && customer.getId() != null
                ? billingStatusRepository.findByCustomerId(customer.getId()).orElse(null)
                : null;

        String invoiceStatus = currentInvoice != null ? resolveInvoiceStatus(currentInvoice, invoiceLog) : "draft";
        String paymentStatus = projection != null ? projection.getCurrentPaymentStatus() : billingStatusSupport.normalizePaymentStatus(latestPayment, currentInvoice);
        String pppoeStatus = projection != null && StringUtils.hasText(projection.getPppoeCurrentState())
                ? projection.getPppoeCurrentState()
                : billingStatusSupport.normalizePppoeStatus(customer != null ? customer.getPppoeStatus() : null);
        Integer overdueDays = projection != null ? projection.getOverdueDays() : 0;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("customerId", customer != null ? customer.getId() : null);
        item.put("customerCode", customer != null ? customer.getCustomerCode() : null);
        item.put("customerName", customer != null ? customer.getFullName() : null);
        item.put("phone", billingStatusSupport.normalizePhone(customer));
        item.put("area", customer != null && customer.getRegion() != null ? customer.getRegion().getName() : null);
        item.put("group", customer != null && customer.getRegion() != null ? customer.getRegion().getName() : null);
        item.put("customerStatus", billingStatusSupport.normalizeCustomerStatus(customer));
        item.put("invoiceId", currentInvoice != null ? currentInvoice.getId() : null);
        item.put("invoiceNumber", currentInvoice != null ? currentInvoice.getInvoiceNumber() : null);
        item.put("invoiceStatus", invoiceStatus);
        item.put("paymentStatus", paymentStatus);
        item.put("pppoeUsername", service != null ? service.getPppoeUsername() : null);
        item.put("pppoeStatus", pppoeStatus);
        item.put("sendMethod", currentInvoice != null ? Optional.ofNullable(currentInvoice.getSendMethod()).orElse("manual") : "manual");
        item.put("lastSentAt", invoiceLog != null ? invoiceLog.getSentAt() : currentInvoice != null ? currentInvoice.getSentAt() : null);
        item.put("lastReceiptSentAt", receiptLog != null ? receiptLog.getSentAt() : latestPayment != null ? latestPayment.getReceiptSentAt() : null);
        item.put("dueDate", currentInvoice != null ? currentInvoice.getDueDate() : null);
        item.put("sendDate", currentInvoice != null && currentInvoice.getDueDate() != null
                ? currentInvoice.getDueDate().minusDays(resolveWhatsappReminderLeadDays())
                : null);
        item.put("amount", currentInvoice != null ? currentInvoice.getTotalAmount() : BigDecimal.ZERO);
        item.put("paidAmount", currentInvoice != null ? currentInvoice.getAmountPaid() : BigDecimal.ZERO);
        item.put("paymentId", latestPayment != null ? latestPayment.getId() : null);
        item.put("overdueDays", overdueDays != null ? overdueDays : 0);
        item.put("eligibleForDisable", projection != null && Boolean.TRUE.equals(projection.getEligibleForDisable()));
        item.put("packageName", service != null && service.getInternetPackage() != null ? service.getInternetPackage().getName() : null);
        item.put("statusFlags", List.of(
                "sent".equals(invoiceStatus) ? "sudah dikirimi invoice" : "belum dikirimi invoice",
                (latestPayment != null && latestPayment.getReceiptSentAt() != null) ? "sudah dikirimi struk pembayaran setelah bayar" : "struk belum dikirim",
                "overdue".equals(invoiceStatus) ? "overdue / telat bayar" : "tidak overdue",
                "disabled".equals(pppoeStatus) ? "PPPoE secret disabled" : "PPPoE secret enabled"
        ));
        return item;
    }

    private Invoice selectCurrentInvoice(List<Invoice> invoices) {
        if (invoices == null || invoices.isEmpty()) {
            return null;
        }

        LocalDate today = LocalDate.now();
        List<Invoice> collectibleInvoices = invoices.stream()
                .filter(this::isCollectibleInvoice)
                .toList();
        if (collectibleInvoices.isEmpty()) {
            return invoices.stream()
                    .max(Comparator.comparing(this::resolveInvoiceReferenceDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(Invoice::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(invoices.getFirst());
        }

        return collectibleInvoices.stream()
                .filter(invoice -> isCurrentOrFuturePeriod(invoice, today))
                .min(Comparator.comparing(this::resolveInvoiceReferenceDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Invoice::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseGet(() -> collectibleInvoices.stream()
                        .max(Comparator.comparing(this::resolveInvoiceReferenceDate, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(Invoice::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                        .orElse(invoices.getFirst()));
    }

    private boolean isOutstanding(Invoice invoice) {
        return !"paid".equals(resolveInvoiceStatus(invoice));
    }

    private boolean isCollectibleInvoice(Invoice invoice) {
        if (invoice == null) {
            return false;
        }
        String status = resolveInvoiceStatus(invoice);
        return !"paid".equals(status)
                && !"cancelled".equalsIgnoreCase(invoice.getStatus())
                && !"no_payment".equals(status);
    }

    private boolean isCurrentOrFuturePeriod(Invoice invoice, LocalDate today) {
        LocalDate referenceDate = resolveInvoiceReferenceDate(invoice);
        return referenceDate != null && !YearMonth.from(referenceDate).isBefore(YearMonth.from(today));
    }

    private LocalDate resolveInvoiceReferenceDate(Invoice invoice) {
        if (invoice == null) {
            return null;
        }
        return invoice.getDueDate() != null ? invoice.getDueDate() : invoice.getBillingMonth();
    }

    private String resolveInvoiceStatus(Invoice invoice) {
        return resolveInvoiceStatus(invoice, null);
    }

    private String resolveInvoiceStatus(Invoice invoice, InvoiceDeliveryLog invoiceLog) {
        String baseStatus = billingStatusSupport.normalizeInvoiceStatus(
                invoice != null ? invoice.getStatus() : null,
                invoice != null ? invoice.getDueDate() : null,
                invoice != null ? invoice.getTotalAmount() : null,
                invoice != null ? invoice.getAmountPaid() : null
        );
        if ("paid".equals(baseStatus) || "overdue".equals(baseStatus)) {
            return baseStatus;
        }
        if (invoiceLog != null && "failed".equalsIgnoreCase(invoiceLog.getStatus())) {
            return "failed";
        }
        if (invoice != null && invoice.getSentAt() != null) {
            return "sent";
        }
        return baseStatus;
    }

    private boolean isCustomerOperationallyActive(Customer customer) {
        return customer != null
                && Boolean.TRUE.equals(customer.getIsActive())
                && !"inactive".equalsIgnoreCase(billingStatusSupport.normalizeCustomerStatus(customer));
    }

    private int resolveWhatsappReminderLeadDays() {
        CompanySetting settings;
        try {
            settings = companySettingRepository.findFirstByIsActiveTrueOrderByIdAsc().orElse(null);
        } catch (DataAccessException ex) {
            if (!companySettingsSchemaSupport.isMissingRelation(ex, "company_settings")) {
                throw ex;
            }
            log.warn("Table company_settings belum tersedia saat membaca lead days reminder. Menggunakan default 3 hari.");
            settings = null;
        }
        Integer leadDays = settings != null ? settings.getWhatsappReminderLeadDays() : null;
        return leadDays != null && leadDays > 0 ? leadDays : 3;
    }

    private Map<String, Object> resolveWhatsappConnection() {
        try {
            var status = whatsappGatewayService.getStatus();
            boolean ready = whatsappGatewayService.isReady(status);
            return Map.of(
                    "status", ready ? "connected" : "disconnected",
                    "label", ready ? "Gateway WhatsApp terhubung" : "Gateway WhatsApp belum siap",
                    "detail", status.getMessage()
            );
        } catch (Exception ex) {
            log.warn("WhatsApp gateway status check failed: {}", ex.getMessage());
            return Map.of(
                    "status", "disconnected",
                    "label", "Gateway WhatsApp belum siap",
                    "detail", ex.getMessage()
            );
        }
    }

    private Map<String, Object> resolveMikrotikConnection() {
        List<MikrotikDevice> devices = mikrotikDeviceRepository.findByIsActiveTrue();
        if (devices.isEmpty()) {
            return Map.of("status", "unknown", "label", "MikroTik belum dikonfigurasi");
        }
        MikrotikDevice device = devices.getFirst();
        try {
            mikrotikRouterOsApiClient.collectIdentitySnapshot(
                    mikrotikConnectionService.resolveTarget(
                            device.getApiIpAddress(),
                            device.getApiPort() != null ? device.getApiPort() : 8728
                    ),
                    device.resolveApiUsername(),
                    device.resolveApiPassword()
            );
            return Map.of("status", "connected", "label", "MikroTik terhubung", "deviceName", device.getName());
        } catch (Exception ex) {
            return Map.of("status", "disconnected", "label", "MikroTik belum terhubung", "detail", ex.getMessage());
        }
    }

    private Map<String, Object> toSchedulerPayload(SchedulerRunLog run) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", run.getId());
        payload.put("jobName", run.getJobName());
        payload.put("executionMode", run.getExecutionMode());
        payload.put("status", run.getStatus());
        payload.put("startedAt", run.getStartedAt());
        payload.put("finishedAt", run.getFinishedAt());
        payload.put("processedCount", run.getProcessedCount());
        payload.put("successCount", run.getSuccessCount());
        payload.put("failedCount", run.getFailedCount());
        payload.put("errorSummary", run.getErrorSummary());
        payload.put("triggeredBy", run.getTriggeredBy());
        return payload;
    }

    private String buildInvoiceMessage(Invoice invoice) {
        Customer customer = invoice.getCustomer();
        CustomerServiceEntity service = invoice.getCustomerService();
        InternetPackage internetPackage = service != null ? service.getInternetPackage() : null;
        String packageName = internetPackage != null ? internetPackage.getName() : "Layanan Internet";
        return String.join("\n",
                "Invoice Tagihan Internet",
                "Halo " + safe(customer != null ? customer.getFullName() : null) + ",",
                "Invoice " + safe(invoice.getInvoiceNumber()) + " untuk paket " + safe(packageName),
                "Nominal: " + formatCurrency(invoice.getTotalAmount()),
                "Jatuh tempo: " + (invoice.getDueDate() != null ? invoice.getDueDate().toString() : "-"),
                "Mohon lakukan pembayaran sebelum jatuh tempo. Terima kasih."
        );
    }

    private String formatCurrency(BigDecimal amount) {
        return "Rp " + java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag("id-ID"))
                .format(amount != null ? amount : BigDecimal.ZERO);
    }

    private String resolveActor(String mode) {
        return "auto".equalsIgnoreCase(mode) || "system".equalsIgnoreCase(mode)
                ? "system"
                : operatorContextService.currentActor();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String stringValue(Map<String, Object> payload, String key) {
        if (payload == null || payload.get(key) == null) {
            return null;
        }
        return String.valueOf(payload.get(key));
    }

    private String safe(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDate.parse(value.trim());
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }
}
