package com.netmaster.nmx.service;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.InvoiceDocumentView;
import com.netmaster.nmx.dto.WhatsappBotHistoryItemData;
import com.netmaster.nmx.dto.WhatsappReminderSettingsData;
import com.netmaster.nmx.dto.WhatsappReminderSettingsRequest;
import com.netmaster.nmx.dto.WhatsappSendDocumentRequest;
import com.netmaster.nmx.dto.WhatsappSendMessageRequest;
import com.netmaster.nmx.dto.WhatsappStatusData;
import com.netmaster.nmx.model.CompanySetting;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.model.Invoice;
import com.netmaster.nmx.model.InternetPackage;
import com.netmaster.nmx.model.WhatsappReminderDispatchLog;
import com.netmaster.nmx.repository.CompanySettingRepository;
import com.netmaster.nmx.repository.InvoiceRepository;
import com.netmaster.nmx.repository.WhatsappReminderDispatchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsappReminderAutomationService {

    private static final Set<Integer> SUPPORTED_LEAD_DAYS = Set.of(1, 3, 7);
    private static final Pattern TEMPLATE_TOKEN_PATTERN = Pattern.compile("#(.*?)#");
    private static final String DEFAULT_INVOICE_TEMPLATE = String.join("\n",
            "\uD83D\uDCC4 INVOICE #nama company#",
            "",
            "Halo Bapak/Ibu \uD83D\uDC4B",
            "Tagihan internet NET Master periode #bulan invoice#/#tahun invoice#:",
            "\uD83D\uDCB3 ID Pelanggan : #id pelanggan#",
            "\uD83D\uDC64 Pelanggan      : Bapak/Ibu #nama pelanggan#",
            "\uD83D\uDCF6 Paket             : #paket pelanggan#",
            "\uD83D\uDCB0 Total              : #tagihan pelanggan#",
            "\u23F0 Jatuh Tempo  : #jatuh tempo pelanggan#",
            "",
            "\uD83C\uDFE6 Nama Bank     : #bank company#",
            "\uD83D\uDCB3 No. Rekening  : #nomor rekening company#",
            "\uD83D\uDC64 Atas Nama      : #nama rekening company#",
            "",
            "Mohon konfirmasi jika telah melakukan pembayaran.",
            "Terima kasih \uD83D\uDE4F"
    );
    private static final String DEFAULT_RECEIPT_COMPANY_NAME = "NET Master";

    private final CompanySettingRepository companySettingRepository;
    private final WhatsappReminderDispatchLogRepository dispatchLogRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceDocumentService invoiceDocumentService;
    private final WhatsappGatewayService whatsappGatewayService;
    private final BillingAuditTrailService billingAuditTrailService;
    private final CompanySettingsSchemaSupport companySettingsSchemaSupport;

    @Value("${nmx.whatsapp.reminder-cron:0 */10 1-23 * * *}")
    private String reminderCronExpression;

    @Value("${nmx.billing.scheduler-zone:Asia/Makassar}")
    private String schedulerZone;

    @Transactional(readOnly = true)
    public WhatsappReminderSettingsData getSettings() {
        return toData(ensureSettings());
    }

    @Transactional
    public WhatsappReminderSettingsData updateSettings(WhatsappReminderSettingsRequest request) {
        validateLeadDays(request.getLeadDays());
        CompanySetting settings = ensureSettings();
        settings.setWhatsappReminderEnabled(Boolean.TRUE.equals(request.getEnabled()));
        settings.setWhatsappReminderLeadDays(request.getLeadDays());
        return toData(companySettingRepository.save(settings));
    }

    @Transactional
    public void dispatchScheduledInvoiceReminders() {
        CompanySetting settings = ensureSettings();
        if (!Boolean.TRUE.equals(settings.getWhatsappReminderEnabled())) {
            return;
        }

        int leadDays = defaultLeadDays(settings);
        LocalDate today = LocalDate.now();
        LocalTime nowTime = LocalTime.now();
        if (nowTime.getHour() < defaultStartHour(settings)) {
            return;
        }

        LocalDate targetDueDate = today.plusDays(leadDays);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime hourStart = now.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime hourEnd = hourStart.plusHours(1);
        int remainingQuota = Math.max(0, defaultHourlyLimit(settings) - (int) dispatchLogRepository.countSentWithinWindow(hourStart, hourEnd));
        if (remainingQuota <= 0) {
            return;
        }

        if (!ensureGatewayReady()) {
            log.warn("WhatsApp reminder dispatch skipped because gateway is not ready");
            return;
        }

        for (Invoice invoice : invoiceRepository.findWhatsappReminderCandidates(targetDueDate)) {
            if (remainingQuota <= 0) {
                return;
            }
            if (invoice == null || invoice.getId() == null) {
                continue;
            }
            if (dispatchLogRepository.existsByInvoiceIdAndDispatchStatusIgnoreCase(invoice.getId(), "sent")) {
                continue;
            }

            String phone = normalizePhone(invoice.getCustomer() != null ? invoice.getCustomer().getPhone() : null);
            if (!StringUtils.hasText(phone)) {
                recordDispatch(invoice, leadDays, targetDueDate, "skipped", "Nomor WhatsApp pelanggan belum tersedia", null, "invoice", null, "skipped", null);
                continue;
            }

            String message = buildReminderMessage(invoice, leadDays);
            try {
                Map<String, Object> gatewayResult = whatsappGatewayService
                        .sendMessage(new WhatsappSendMessageRequest(phone, null, message))
                        .getData();
                recordDispatch(
                        invoice,
                        leadDays,
                        targetDueDate,
                        "sent",
                        "Reminder invoice berhasil dikirim",
                        now,
                        "invoice",
                        stringValue(gatewayResult, "messageId"),
                        "sent",
                        null
                );
                billingAuditTrailService.record(
                        "WHATSAPP_REMINDER_SEND",
                        "Reminder invoice " + safe(invoice.getInvoiceNumber()) + " dikirim ke " + phone
                );
                remainingQuota -= 1;
            } catch (RuntimeException ex) {
                recordDispatch(invoice, leadDays, targetDueDate, "error", ex.getMessage(), null, "invoice", null, "error", null);
                log.warn("Failed sending WhatsApp reminder for invoice {}: {}", invoice.getId(), ex.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public Map<String, String> buildManualInvoiceDraft(Long invoiceId) {
        Invoice invoice = invoiceRepository.findDocumentById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice tidak ditemukan"));

        boolean paid = "paid".equals(resolveStatus(invoice));
        if (paid) {
            throw new IllegalArgumentException("Draft WhatsApp manual hanya tersedia untuk invoice/tagihan yang belum lunas");
        }

        String phone = normalizePhone(invoice.getCustomer() != null ? invoice.getCustomer().getPhone() : null);
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("Nomor WhatsApp pelanggan belum tersedia");
        }

        return Map.of(
                "invoiceId", String.valueOf(invoice.getId()),
                "invoiceNumber", safe(invoice.getInvoiceNumber()),
                "customerName", safe(invoice.getCustomer() != null ? invoice.getCustomer().getFullName() : null),
                "phone", phone,
                "documentType", "invoice",
                "documentLabel", "invoice/tagihan",
                "message", buildManualInvoiceMessage(invoice)
        );
    }

    @Transactional
    public Map<String, String> sendManualInvoiceOrReceipt(Long invoiceId) {
        Invoice invoice = invoiceRepository.findDocumentById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice tidak ditemukan"));

        String phone = normalizePhone(invoice.getCustomer() != null ? invoice.getCustomer().getPhone() : null);
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("Nomor WhatsApp pelanggan belum tersedia");
        }

        if (!ensureGatewayReady()) {
            throw new IllegalStateException("WhatsApp gateway belum terhubung");
        }

        boolean paid = "paid".equals(resolveStatus(invoice));
        Map<String, Object> gatewayResult;
        if (paid) {
            gatewayResult = sendPaymentReceiptPdf(invoice, phone);
        } else {
            String message = buildManualInvoiceMessage(invoice);
            gatewayResult = whatsappGatewayService
                    .sendMessage(new WhatsappSendMessageRequest(phone, null, message))
                    .getData();
        }

        String documentType = paid ? "receipt" : "invoice";
        String documentLabel = paid ? "struk pembayaran" : "invoice/tagihan";
        String auditAction = paid ? "WHATSAPP_RECEIPT_SEND_MANUAL" : "WHATSAPP_INVOICE_SEND_MANUAL";
        billingAuditTrailService.record(
                auditAction,
                "Kirim manual " + documentLabel + " " + safe(invoice.getInvoiceNumber()) + " ke " + phone
        );
        recordDispatch(
                invoice,
                0,
                LocalDate.now(),
                "sent",
                "Kirim manual " + documentLabel + " berhasil",
                LocalDateTime.now(),
                documentType,
                stringValue(gatewayResult, "messageId"),
                "sent",
                null
        );

        return Map.of(
                "invoiceNumber", safe(invoice.getInvoiceNumber()),
                "phone", phone,
                "documentType", documentType,
                "documentLabel", documentLabel
        );
    }

    @Transactional(readOnly = true)
    public Map<String, String> resolveManualSendResultAfterRollback(Long invoiceId) {
        Invoice invoice = invoiceRepository.findDocumentById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice tidak ditemukan"));

        boolean paid = "paid".equals(resolveStatus(invoice));
        String documentType = paid ? "receipt" : "invoice";
        String documentLabel = paid ? "struk pembayaran" : "invoice/tagihan";
        String phone = normalizePhone(invoice.getCustomer() != null ? invoice.getCustomer().getPhone() : null);

        return Map.of(
                "invoiceNumber", safe(invoice.getInvoiceNumber()),
                "phone", safe(phone),
                "documentType", documentType,
                "documentLabel", documentLabel
        );
    }

    @Transactional
    public void sendAutomaticPaymentReceipt(Long invoiceId) {
        Invoice invoice = invoiceRepository.findDocumentById(invoiceId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice tidak ditemukan"));

        if (!"paid".equals(resolveStatus(invoice))) {
            throw new IllegalArgumentException("Struk pembayaran otomatis hanya tersedia untuk invoice yang sudah lunas");
        }

        String phone = normalizePhone(invoice.getCustomer() != null ? invoice.getCustomer().getPhone() : null);
        if (!StringUtils.hasText(phone)) {
            throw new IllegalArgumentException("Nomor WhatsApp pelanggan belum tersedia");
        }

        if (!ensureGatewayReady()) {
            throw new IllegalStateException("WhatsApp gateway belum terhubung");
        }

        Map<String, Object> gatewayResult = sendPaymentReceiptPdf(invoice, phone);
        billingAuditTrailService.record(
                "WHATSAPP_RECEIPT_SEND_AUTO",
                "Kirim otomatis struk pembayaran " + safe(invoice.getInvoiceNumber()) + " ke " + phone
        );
        recordDispatch(
                invoice,
                -1,
                LocalDate.now(),
                "sent",
                "Kirim otomatis struk pembayaran berhasil",
                LocalDateTime.now(),
                "receipt",
                stringValue(gatewayResult, "messageId"),
                "sent",
                null
        );
    }

    @Transactional(readOnly = true)
    public List<WhatsappBotHistoryItemData> getBotHistory() {
        List<WhatsappBotHistoryItemData> items = new ArrayList<>();
        try {
            for (WhatsappReminderDispatchLog logEntry : dispatchLogRepository.findRecentHistory()) {
                if (logEntry == null || logEntry.getInvoice() == null) {
                    continue;
                }
                items.add(syncHistoryStatus(logEntry));
            }
        } catch (DataAccessException ex) {
            if (isLegacyDispatchLogSchemaMismatch(ex)) {
                log.warn("Schema whatsapp_reminder_dispatch_logs belum sinkron untuk history BOT WA: {}", ex.getMessage());
                return List.of();
            }
            throw ex;
        }
        return items;
    }

    @Transactional
    public Map<String, Object> getScheduleOverview() {
        CompanySetting settings = ensureSettings();
        int leadDays = defaultLeadDays(settings);
        LocalDate today = LocalDate.now();
        LocalDate targetDueDate = today.plusDays(leadDays);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime hourStart = now.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime hourEnd = hourStart.plusHours(1);
        int hourlyLimit = defaultHourlyLimit(settings);
        long sentThisHour = dispatchLogRepository.countSentWithinWindow(hourStart, hourEnd);

        ApiResponse<WhatsappStatusData> gatewayStatus = whatsappGatewayService.getStatus();
        boolean gatewayReady = gatewayStatus.getData() != null && whatsappGatewayService.isReady(gatewayStatus);

        List<Map<String, Object>> automaticQueue = invoiceRepository.findWhatsappReminderCandidates(targetDueDate).stream()
                .filter(invoice -> invoice != null && invoice.getId() != null)
                .map(invoice -> toAutomaticScheduleRow(invoice, leadDays, targetDueDate))
                .toList();

        List<WhatsappBotHistoryItemData> history = getBotHistory().stream()
                .sorted((left, right) -> {
                    LocalDateTime leftTime = left.getSentAt() != null ? left.getSentAt() : LocalDateTime.MIN;
                    LocalDateTime rightTime = right.getSentAt() != null ? right.getSentAt() : LocalDateTime.MIN;
                    return rightTime.compareTo(leftTime);
                })
                .toList();

        List<WhatsappBotHistoryItemData> manualHistory = history.stream()
                .filter(item -> "manual".equalsIgnoreCase(item.getDispatchMode()))
                .limit(12)
                .collect(Collectors.toList());

        List<WhatsappBotHistoryItemData> automaticHistory = history.stream()
                .filter(item -> "automatic".equalsIgnoreCase(item.getDispatchMode()))
                .limit(12)
                .collect(Collectors.toList());

        long automaticReady = automaticQueue.stream()
                .filter(item -> "ready".equals(item.get("scheduleStatus")))
                .count();
        long automaticMissingPhone = automaticQueue.stream()
                .filter(item -> "missing_phone".equals(item.get("scheduleStatus")))
                .count();
        long automaticAlreadySent = automaticQueue.stream()
                .filter(item -> "already_sent".equals(item.get("scheduleStatus")))
                .count();
        long manualSentToday = history.stream()
                .filter(item -> "manual".equalsIgnoreCase(item.getDispatchMode()))
                .filter(WhatsappBotHistoryItemData::isSent)
                .filter(item -> item.getSentAt() != null && item.getSentAt().toLocalDate().equals(today))
                .count();
        long automaticSentToday = history.stream()
                .filter(item -> "automatic".equalsIgnoreCase(item.getDispatchMode()))
                .filter(WhatsappBotHistoryItemData::isSent)
                .filter(item -> item.getSentAt() != null && item.getSentAt().toLocalDate().equals(today))
                .count();

        return Map.of(
                "settings", getSettings(),
                "gateway", Map.of(
                        "ready", gatewayReady,
                        "statusLabel", gatewayReady ? "Terhubung" : "Belum siap",
                        "cronExpression", reminderCronExpression,
                        "timezone", schedulerZone,
                        "nextDispatchTargetDate", targetDueDate,
                        "sendStartHour", defaultStartHour(settings),
                        "hourlyLimit", hourlyLimit,
                        "sentThisHour", sentThisHour,
                        "remainingQuota", Math.max(0, hourlyLimit - sentThisHour)
                ),
                "stats", Map.of(
                        "automaticQueue", automaticQueue.size(),
                        "automaticReady", automaticReady,
                        "automaticMissingPhone", automaticMissingPhone,
                        "automaticAlreadySent", automaticAlreadySent,
                        "automaticSentToday", automaticSentToday,
                        "manualSentToday", manualSentToday
                ),
                "automaticQueue", automaticQueue,
                "automaticHistory", automaticHistory,
                "manualHistory", manualHistory
        );
    }

    private WhatsappBotHistoryItemData syncHistoryStatus(WhatsappReminderDispatchLog logEntry) {
        String deliveryStatus = safe(logEntry.getDeliveryStatus());
        Integer ack = null;
        LocalDateTime readAt = logEntry.getReadAt();

        if (StringUtils.hasText(logEntry.getMessageId())) {
            try {
                ApiResponse<Map<String, Object>> response = whatsappGatewayService.getMessageStatus(logEntry.getMessageId());
                Map<String, Object> payload = response.getData();
                ack = integerValue(payload, "ack");
                deliveryStatus = resolveDeliveryStatus(logEntry.getDispatchStatus(), ack);
                if ("read".equals(deliveryStatus) && readAt == null) {
                    readAt = LocalDateTime.now();
                }
            } catch (RuntimeException ex) {
                if (!StringUtils.hasText(deliveryStatus)) {
                    deliveryStatus = resolveDeliveryStatus(logEntry.getDispatchStatus(), null);
                }
            }
        } else if (!StringUtils.hasText(deliveryStatus)) {
            deliveryStatus = resolveDeliveryStatus(logEntry.getDispatchStatus(), null);
        }

        Invoice invoice = logEntry.getInvoice();
        return WhatsappBotHistoryItemData.builder()
                .logId(logEntry.getId())
                .invoiceId(invoice.getId())
                .invoiceNumber(safe(invoice.getInvoiceNumber()))
                .customerName(safe(invoice.getCustomer() != null ? invoice.getCustomer().getFullName() : null))
                .customerCode(safe(invoice.getCustomer() != null ? invoice.getCustomer().getCustomerCode() : null))
                .phoneNumber(safe(logEntry.getPhoneNumber()))
                .documentType(safe(logEntry.getDocumentType()))
                .documentLabel(resolveDocumentLabel(logEntry.getDocumentType()))
                .dispatchStatus(safe(logEntry.getDispatchStatus()))
                .dispatchStatusLabel(resolveDispatchStatusLabel(logEntry.getDispatchStatus()))
                .deliveryStatus(deliveryStatus)
                .deliveryStatusLabel(resolveDeliveryStatusLabel(deliveryStatus))
                .ack(ack)
                .sent(isSentStatus(deliveryStatus, logEntry.getDispatchStatus()))
                .delivered(isDeliveredStatus(deliveryStatus))
                .read(isReadStatus(deliveryStatus))
                .leadDays(logEntry.getLeadDays())
                .dispatchMode(resolveDispatchMode(logEntry.getLeadDays()))
                .dispatchModeLabel(resolveDispatchModeLabel(logEntry.getLeadDays()))
                .scheduledForDate(logEntry.getScheduledForDate())
                .sentAt(logEntry.getSentAt())
                .readAt(readAt)
                .gatewayMessage(safe(logEntry.getGatewayMessage()))
                .messageId(safe(logEntry.getMessageId()))
                .build();
    }

    private Map<String, Object> toAutomaticScheduleRow(Invoice invoice, int leadDays, LocalDate scheduledForDate) {
        String phone = normalizePhone(invoice.getCustomer() != null ? invoice.getCustomer().getPhone() : null);
        boolean alreadySent = dispatchLogRepository.existsByInvoiceIdAndDispatchStatusIgnoreCase(invoice.getId(), "sent");
        String scheduleStatus = alreadySent ? "already_sent" : (StringUtils.hasText(phone) ? "ready" : "missing_phone");
        String statusLabel = switch (scheduleStatus) {
            case "already_sent" -> "Sudah terkirim";
            case "missing_phone" -> "Nomor WA belum ada";
            default -> "Siap dikirim";
        };

        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("invoiceId", invoice.getId());
        payload.put("invoiceNumber", safe(invoice.getInvoiceNumber()));
        payload.put("customerName", safe(invoice.getCustomer() != null ? invoice.getCustomer().getFullName() : null));
        payload.put("customerCode", safe(invoice.getCustomer() != null ? invoice.getCustomer().getCustomerCode() : null));
        payload.put("phoneNumber", safe(phone));
        payload.put("dueDate", invoice.getDueDate());
        payload.put("scheduledForDate", scheduledForDate);
        payload.put("leadDays", leadDays);
        payload.put("amount", invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO);
        payload.put("documentType", "invoice");
        payload.put("dispatchMode", "automatic");
        payload.put("dispatchModeLabel", "Otomatis");
        payload.put("scheduleStatus", scheduleStatus);
        payload.put("scheduleStatusLabel", statusLabel);
        payload.put("packageName", resolvePackageName(invoice));
        return payload;
    }

    private void recordDispatch(Invoice invoice, Integer leadDays, LocalDate scheduledDate, String status, String message,
                                LocalDateTime sentAt, String documentType, String messageId, String deliveryStatus,
                                LocalDateTime readAt) {
        WhatsappReminderDispatchLog logEntry = new WhatsappReminderDispatchLog();
        logEntry.setInvoice(invoice);
        logEntry.setPhoneNumber(invoice != null && invoice.getCustomer() != null ? normalizePhone(invoice.getCustomer().getPhone()) : null);
        logEntry.setLeadDays(leadDays != null ? leadDays : 3);
        logEntry.setScheduledForDate(scheduledDate != null ? scheduledDate : LocalDate.now());
        logEntry.setDispatchStatus(status);
        logEntry.setDocumentType(documentType);
        logEntry.setMessageId(messageId);
        logEntry.setDeliveryStatus(deliveryStatus);
        logEntry.setGatewayMessage(message);
        logEntry.setSentAt(sentAt);
        logEntry.setReadAt(readAt);
        dispatchLogRepository.save(logEntry);
    }

    private CompanySetting ensureSettings() {
        try {
            return findOrCreateSettings();
        } catch (DataAccessException ex) {
            if (!companySettingsSchemaSupport.isMissingRelation(ex, "company_settings")) {
                throw ex;
            }
            log.warn("Table company_settings belum tersedia. Menggunakan default pengaturan reminder WhatsApp tanpa menulis schema runtime.");
            return defaultSettings();
        }
    }

    private CompanySetting findOrCreateSettings() {
        return companySettingRepository.findFirstByIsActiveTrueOrderByIdAsc()
                .orElseGet(() -> companySettingRepository.save(defaultSettings()));
    }

    private CompanySetting defaultSettings() {
        CompanySetting setting = new CompanySetting();
        setting.setIsActive(true);
        setting.setWhatsappReminderEnabled(false);
        setting.setWhatsappReminderLeadDays(3);
        setting.setWhatsappHourlyLimit(6);
        setting.setWhatsappBatchIntervalMinutes(10);
        setting.setWhatsappSendStartHour(1);
        return setting;
    }

    private void validateLeadDays(Integer leadDays) {
        if (leadDays == null || !SUPPORTED_LEAD_DAYS.contains(leadDays)) {
            throw new IllegalArgumentException("Durasi reminder WhatsApp hanya mendukung -1, -3, atau -7 hari.");
        }
    }

    private WhatsappReminderSettingsData toData(CompanySetting settings) {
        int leadDays = defaultLeadDays(settings);
        return new WhatsappReminderSettingsData(
                Boolean.TRUE.equals(settings.getWhatsappReminderEnabled()),
                leadDays,
                defaultHourlyLimit(settings),
                defaultBatchInterval(settings),
                defaultStartHour(settings),
                "Invoice jatuh tempo tanggal 5 akan dikirim tanggal " + (5 - leadDays) + " bila opsi -" + leadDays + " hari dipilih."
        );
    }

    private String buildReminderMessage(Invoice invoice, Integer leadDays) {
        return renderInvoiceTemplate(invoice);
    }

    private String buildManualInvoiceMessage(Invoice invoice) {
        return renderInvoiceTemplate(invoice);
    }

    private boolean ensureGatewayReady() {
        ApiResponse<?> gatewayStatus = whatsappGatewayService.getStatus();
        if (gatewayStatus.getData() != null && whatsappGatewayService.isReady(gatewayStatus)) {
            return true;
        }
        try {
            whatsappGatewayService.initClient();
        } catch (RuntimeException ex) {
            log.warn("Auto init WhatsApp gateway gagal: {}", ex.getMessage());
        }
        ApiResponse<?> latestStatus = whatsappGatewayService.getStatus();
        return latestStatus.getData() != null && whatsappGatewayService.isReady(latestStatus);
    }

    private Map<String, Object> sendPaymentReceiptPdf(Invoice invoice, String phone) {
        InvoiceDocumentView document = invoiceDocumentService.getInvoiceDocument(invoice.getId());
        byte[] pdfBytes = invoiceDocumentService.renderInvoiceDocumentPdf(invoice.getId(), "80");
        String base64Data = Base64.getEncoder().encodeToString(pdfBytes);
        String fileName = buildReceiptPdfFileName(document);
        return whatsappGatewayService.sendDocument(new WhatsappSendDocumentRequest(
                phone,
                "application/pdf",
                fileName,
                base64Data,
                buildReceiptCaption(document)
        )).getData();
    }

    private String buildReceiptCaption(InvoiceDocumentView document) {
        String companyName = document != null && StringUtils.hasText(document.getCompanyName())
                ? document.getCompanyName().trim()
                : DEFAULT_RECEIPT_COMPANY_NAME;
        return "Terima kasih telah melakukan pembayaran layanan internet " + companyName + ".";
    }

    private String resolveStatus(Invoice invoice) {
        String normalized = String.valueOf(invoice != null ? invoice.getStatus() : "")
                .trim()
                .toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "paid", "lunas" -> "paid";
            case "cancelled", "dibatalkan", "batal" -> "cancelled";
            case "overdue", "jatuh tempo" -> "overdue";
            default -> "unpaid";
        };
    }

    private String formatDate(LocalDate date) {
        if (date == null) {
            return "-";
        }
        return date.getDayOfMonth() + " "
                + date.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("id-ID"))
                + " " + date.getYear();
    }

    private String formatCurrency(BigDecimal amount) {
        Locale locale = Locale.forLanguageTag("id-ID");
        NumberFormat rupiah = NumberFormat.getCurrencyInstance(locale);
        return amount != null ? rupiah.format(amount) : "Rp0";
    }

    private String resolveDocumentLabel(String documentType) {
        return "receipt".equalsIgnoreCase(documentType) ? "Struk Pembayaran" : "Invoice/Tagihan";
    }

    private String resolveDispatchStatusLabel(String dispatchStatus) {
        String normalized = safe(dispatchStatus).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "sent" -> "Terkirim ke Gateway";
            case "error" -> "Gagal Dikirim";
            case "skipped" -> "Dilewati";
            default -> "Menunggu";
        };
    }

    private String resolveDeliveryStatus(String dispatchStatus, Integer ack) {
        String normalizedDispatch = safe(dispatchStatus).toLowerCase(Locale.ROOT);
        if ("error".equals(normalizedDispatch)) {
            return "error";
        }
        if ("skipped".equals(normalizedDispatch)) {
            return "skipped";
        }
        if (ack == null) {
            return "sent";
        }
        if (ack >= 3) {
            return "read";
        }
        if (ack >= 2) {
            return "delivered";
        }
        if (ack >= 0) {
            return "sent";
        }
        return "error";
    }

    private String resolveDeliveryStatusLabel(String deliveryStatus) {
        String normalized = safe(deliveryStatus).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "read" -> "Sudah Dibaca";
            case "delivered" -> "Berhasil Terkirim";
            case "sent" -> "Terkirim";
            case "skipped" -> "Dilewati";
            case "error" -> "Gagal";
            default -> "Menunggu";
        };
    }

    private boolean isSentStatus(String deliveryStatus, String dispatchStatus) {
        if ("error".equalsIgnoreCase(safe(dispatchStatus)) || "skipped".equalsIgnoreCase(safe(dispatchStatus))) {
            return false;
        }
        String normalized = safe(deliveryStatus).toLowerCase(Locale.ROOT);
        return "sent".equals(normalized) || "delivered".equals(normalized) || "read".equals(normalized);
    }

    private boolean isDeliveredStatus(String deliveryStatus) {
        String normalized = safe(deliveryStatus).toLowerCase(Locale.ROOT);
        return "delivered".equals(normalized) || "read".equals(normalized);
    }

    private boolean isReadStatus(String deliveryStatus) {
        return "read".equalsIgnoreCase(safe(deliveryStatus));
    }

    private String resolveDispatchMode(Integer leadDays) {
        return leadDays != null && leadDays == 0 ? "manual" : "automatic";
    }

    private String resolveDispatchModeLabel(Integer leadDays) {
        return leadDays != null && leadDays == 0 ? "Manual" : "Otomatis";
    }

    private String stringValue(Map<String, Object> payload, String key) {
        if (payload == null || !payload.containsKey(key) || payload.get(key) == null) {
            return null;
        }
        return String.valueOf(payload.get(key)).trim();
    }

    private Integer integerValue(Map<String, Object> payload, String key) {
        if (payload == null || !payload.containsKey(key) || payload.get(key) == null) {
            return null;
        }
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
    private String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) {
            digits = "62" + digits.substring(1);
        }
        if (!digits.startsWith("62")) {
            digits = "62" + digits;
        }
        return digits;
    }

    private int defaultLeadDays(CompanySetting settings) {
        return settings.getWhatsappReminderLeadDays() != null ? settings.getWhatsappReminderLeadDays() : 3;
    }

    private int defaultHourlyLimit(CompanySetting settings) {
        return settings.getWhatsappHourlyLimit() != null ? settings.getWhatsappHourlyLimit() : 6;
    }

    private int defaultBatchInterval(CompanySetting settings) {
        return settings.getWhatsappBatchIntervalMinutes() != null ? settings.getWhatsappBatchIntervalMinutes() : 10;
    }

    private int defaultStartHour(CompanySetting settings) {
        return settings.getWhatsappSendStartHour() != null ? settings.getWhatsappSendStartHour() : 1;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "-";
    }

    private boolean isLegacyDispatchLogSchemaMismatch(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                boolean dispatchTableMentioned = normalized.contains("whatsapp_reminder_dispatch_logs")
                        || normalized.contains("wrdl")
                        || normalized.contains("dispatch_logs");
                boolean missingColumn = normalized.contains("column")
                        && normalized.contains("does not exist");
                if (dispatchTableMentioned && missingColumn) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private String buildReceiptPdfFileName(InvoiceDocumentView document) {
        String invoiceNumber = sanitizeFileNamePart(document.getInvoiceNumber(), "STRUK-PEMBAYARAN");
        LocalDate paymentMonthDate = document.getPaymentDate() != null
                ? document.getPaymentDate()
                : document.getDueDate() != null ? document.getDueDate() : document.getIssueDate();
        String paymentMonth = paymentMonthDate != null
                ? paymentMonthDate.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("id-ID")).toUpperCase(Locale.ROOT)
                : "PEMBAYARAN";
        String customerName = sanitizeFileNamePart(document.getCustomerName(), "PELANGGAN");
        return invoiceNumber + "-" + sanitizeFileNamePart(paymentMonth, "PEMBAYARAN") + "-" + customerName + "-80mm.pdf";
    }

    private String sanitizeFileNamePart(String value, String fallback) {
        String baseValue = value != null && !value.isBlank() ? value : fallback;
        String normalized = Normalizer.normalize(baseValue, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{Alnum}]+", "-")
                .replaceAll("(^-+|-+$)", "")
                .replaceAll("-{2,}", "-")
                .toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String renderInvoiceTemplate(Invoice invoice) {
        InvoiceDocumentView document = invoiceDocumentService.getInvoiceDocument(invoice.getId());
        Matcher matcher = TEMPLATE_TOKEN_PATTERN.matcher(DEFAULT_INVOICE_TEMPLATE);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = resolveTemplateToken(token, invoice, document);
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String resolveTemplateToken(String token, Invoice invoice, InvoiceDocumentView document) {
        String normalizedToken = normalizeToken(token);
        LocalDate periodDate = invoice.getBillingMonth() != null
                ? invoice.getBillingMonth()
                : (invoice.getDueDate() != null ? invoice.getDueDate() : LocalDate.now());

        return switch (normalizedToken) {
            case "bulan berjalan", "bulan invoice" -> periodDate.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("id-ID"));
            case "tahun berjalan", "tahun invoice" -> String.valueOf(periodDate.getYear());
            case "nama company", "data nama company", "data nama perusahaan" -> safe(document.getCompanyName());
            case "id pelanggan", "data id pelanggan" -> safe(document.getCustomerCode());
            case "nama pelangan", "nama pelanggan", "data nama pelanggan" -> safe(document.getCustomerName());
            case "paket pelanggan", "data paket internet pelanggan" -> resolvePackageName(invoice);
            case "tagihan pelanggan", "data harga paket internet pelanggan" -> formatCurrency(invoice.getTotalAmount());
            case "jatuh tempo pelanggan", "data jatuh tempo pelanggan" -> formatDate(invoice.getDueDate());
            case "bank company", "data nama bank company" -> safe(document.getPaymentBankName());
            case "nomor rekening company", "data nomor rekening company" -> safe(document.getPaymentAccountNumber());
            case "nama rekening company", "data nama pemilik bank company" -> safe(document.getPaymentAccountName());
            default -> "#" + token + "#";
        };
    }

    private String normalizeToken(String token) {
        return String.valueOf(token)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private String resolvePackageName(Invoice invoice) {
        CustomerServiceEntity service = invoice != null ? invoice.getCustomerService() : null;
        InternetPackage internetPackage = service != null ? service.getInternetPackage() : null;
        if (internetPackage != null && StringUtils.hasText(internetPackage.getName())) {
            return internetPackage.getName().trim();
        }
        return safe(invoice != null ? invoice.getInvoiceType() : null);
    }
}









