package com.netmaster.nmx.service;

import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.netmaster.nmx.dto.CustomerBillingSummaryDTO;
import com.netmaster.nmx.dto.InvoiceRowDTO;
import com.netmaster.nmx.dto.PaymentHistoryItemDTO;
import com.netmaster.nmx.dto.QuickPayInvoiceRequest;
import com.netmaster.nmx.dto.RecordPaymentRequest;
import com.netmaster.nmx.model.Customer;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.model.Invoice;
import com.netmaster.nmx.model.Payment;
import com.netmaster.nmx.model.PaymentHistoryEntry;
import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import com.netmaster.nmx.repository.InvoiceRepository;
import com.netmaster.nmx.repository.PaymentRepository;
import com.netmaster.nmx.repository.PaymentHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingQuickActionService {

    private static final String HISTORY_DESCRIPTION_MONTHLY = "BULANAN";
    private static final String HISTORY_DESCRIPTION_ACTIVATION = "AKTIVASI";
    private static final String HISTORY_DESCRIPTION_NO_PAYMENT = "TIDAK_BAYAR";
    private static final String HISTORY_METHOD_SYSTEM = "SYSTEM";
    private static final String INVOICE_STATUS_NO_PAYMENT = "no_payment";

    private final CustomerRepository customerRepository;
    private final CustomerServiceEntityRepository customerServiceEntityRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final BillingInvoiceService billingInvoiceService;
    private final PaymentManagementService paymentManagementService;
    private final InvoiceNumberService invoiceNumberService;
    private final BillingAuditTrailService billingAuditTrailService;
    private final BillingPppoeAutomationService billingPppoeAutomationService;
    private final WhatsappReminderAutomationService whatsappReminderAutomationService;
    private final WhatsappGatewayService whatsappGatewayService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<PaymentHistoryItemDTO> getCustomerPayments(Long customerId, LocalDate startDate, LocalDate endDate) {
        getCustomer(customerId);
        if (!tableExists("payment_history")) {
            return paymentRepository.findByCustomerIdOrderByPaymentDateAscIdAsc(customerId).stream()
                    .filter(payment -> matchesPaymentDateRange(payment, startDate, endDate))
                    .map(this::toPaymentHistoryItem)
                    .toList();
        }
        try {
            List<PaymentHistoryEntry> entries;
            if (startDate != null && endDate != null) {
                entries = paymentHistoryRepository.findByCustomerIdAndPaymentDateBetweenOrderByPaymentDateAscIdAsc(customerId, startDate, endDate);
            } else {
                entries = paymentHistoryRepository.findByCustomerIdOrderByPaymentDateAscIdAsc(customerId);
            }
            return entries.stream()
                    .map(this::toPaymentHistoryItem)
                    .toList();
        } catch (DataAccessException ex) {
            if (!isMissingRelation(ex, "payment_history")) {
                throw ex;
            }
            log.warn("payment_history table is unavailable for customer {}. Falling back to payments table.", customerId);
            return paymentRepository.findByCustomerIdOrderByPaymentDateAscIdAsc(customerId).stream()
                    .filter(payment -> matchesPaymentDateRange(payment, startDate, endDate))
                    .map(this::toPaymentHistoryItem)
                    .toList();
        }
    }

    @Transactional(readOnly = true)
    public List<InvoiceRowDTO> getCustomerInvoices(Long customerId, String status) {
        getCustomer(customerId);
        String normalizedStatus = normalizeInvoiceStatusFilter(status);
        return billingInvoiceService.getInvoiceRowsByCustomer(customerId).stream()
                .filter(row -> matchesInvoiceStatus(row, normalizedStatus))
                .sorted(Comparator.comparing(InvoiceRowDTO::getDueDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(InvoiceRowDTO::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerBillingSummaryDTO getCustomerBillingSummary(Long customerId) {
        List<InvoiceRowDTO> invoices = getCustomerInvoices(customerId, null);
        BigDecimal totalUnpaid = invoices.stream()
                .filter(this::isOutstanding)
                .map(InvoiceRowDTO::getOutstandingAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid;
        LocalDate lastPaymentDate;
        if (tableExists("payment_history")) {
            try {
                totalPaid = paymentHistoryRepository.sumAmountByCustomerId(customerId);
                lastPaymentDate = paymentHistoryRepository.findLastPaymentDateByCustomerId(customerId);
            } catch (DataAccessException ex) {
                if (!isMissingRelation(ex, "payment_history")) {
                    throw ex;
                }
                List<Payment> payments = paymentRepository.findByCustomerIdOrderByPaymentDateAscIdAsc(customerId);
                totalPaid = payments.stream()
                        .map(Payment::getAmountPaid)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                lastPaymentDate = payments.stream()
                        .map(Payment::getPaymentDate)
                        .filter(Objects::nonNull)
                        .max(LocalDate::compareTo)
                        .orElse(null);
            }
        } else {
            List<Payment> payments = paymentRepository.findByCustomerIdOrderByPaymentDateAscIdAsc(customerId);
            totalPaid = payments.stream()
                    .map(Payment::getAmountPaid)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            lastPaymentDate = payments.stream()
                    .map(Payment::getPaymentDate)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(null);
        }
        return new CustomerBillingSummaryDTO(customerId, defaultAmount(totalPaid), defaultAmount(totalUnpaid), lastPaymentDate);
    }

    @Transactional
    public InvoiceRowDTO payInvoice(Long invoiceId, QuickPayInvoiceRequest request) {
        InvoiceRowDTO currentInvoice = billingInvoiceService.getInvoiceRowById(invoiceId);
        if (INVOICE_STATUS_NO_PAYMENT.equalsIgnoreCase(currentInvoice.getStatus())
                || "tidak_bayar".equalsIgnoreCase(currentInvoice.getStatus())) {
            throw new RuntimeException("Invoice bulan sebelumnya sudah ditutup sebagai Tidak Bayar dan hanya disimpan sebagai histori.");
        }
        if ("paid".equalsIgnoreCase(currentInvoice.getStatus())) {
            return currentInvoice;
        }
        if (!isOutstanding(currentInvoice)) {
            throw new RuntimeException("Invoice tidak dapat dibayar.");
        }

        LocalDate paymentDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();
        BigDecimal fullAmount = defaultAmount(currentInvoice.getOutstandingAmount());
        BigDecimal requestedAmount = defaultAmount(request.getAmount());
        if (fullAmount.signum() <= 0) {
            throw new RuntimeException("Invoice tidak memiliki sisa tagihan.");
        }
        if (request.getAmount() != null && requestedAmount.compareTo(fullAmount) != 0) {
            throw new RuntimeException("Pembayaran harus melunasi seluruh sisa tagihan.");
        }

        InvoiceRowDTO paidInvoice = paymentManagementService.recordPayment(
                invoiceId,
                new RecordPaymentRequest(fullAmount, paymentDate, request.getPaymentMethod(), null, request.getNotes())
        );
        schedulePostPaymentActions(invoiceId, paidInvoice, paymentDate, request.getPaymentMethod());
        return billingInvoiceService.getInvoiceRowById(invoiceId);
    }

    @Transactional
    public void suspendCustomer(Long customerId, String reason) {
        Customer customer = getCustomer(customerId);
        CustomerServiceEntity service = customerServiceEntityRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customerId)
                .orElse(null);
        try {
            billingPppoeAutomationService.disableCustomer(customerId, null, null, reason, "manual");
        } catch (Exception ex) {
            log.warn("Failed to disable PPPoE for customer {}: {}", customer.getCustomerCode(), ex.getMessage());
        }

        if (service != null) {
            service.setStatus("suspended");
            customerServiceEntityRepository.save(service);
        }
        customer.setStatus("suspended");
        customer.setIsActive(false);
        customerRepository.save(customer);
        billingAuditTrailService.record(
                "SUSPEND",
                "Layanan pelanggan " + customer.getCustomerCode() + " disuspend."
                        + (StringUtils.hasText(reason) ? " Alasan: " + reason.trim() : "")
        );
    }

    @Transactional
    public void softDeleteCustomer(Long customerId) {
        Customer customer = getCustomer(customerId);
        CustomerServiceEntity service = customerServiceEntityRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customerId)
                .orElse(null);
        if (service != null) {
            try {
                billingPppoeAutomationService.disableCustomer(customerId, null, null, "Terminasi pelanggan", "manual");
            } catch (Exception ex) {
                log.warn("Unable to disable PPPoE during customer termination {}: {}", customer.getCustomerCode(), ex.getMessage());
            }
            releaseServiceUniqueFields(service, customer);
            service.setStatus("terminated");
            customerServiceEntityRepository.save(service);
        }

        releaseCustomerUniqueFields(customer);
        customer.setStatus("terminated");
        customer.setIsDeleted(Boolean.TRUE);
        customerRepository.save(customer);
        billingAuditTrailService.record("DELETE_CUSTOMER", "Pelanggan " + customer.getCustomerCode() + " diterminasi dan di-soft delete.");
    }

    @Transactional
    public int generateMonthlyInvoices() {
        LocalDate today = LocalDate.now();
        markMissedPaymentsAtMonthEnd(today);
        YearMonth currentMonth = YearMonth.from(today);
        final int[] generatedCount = {0};
        customerRepository.findAll().stream()
                .filter(customer -> !Boolean.TRUE.equals(customer.getIsDeleted()))
                .filter(customer -> !"terminated".equalsIgnoreCase(customer.getStatus()))
                .forEach(customer -> {
                    CustomerServiceEntity service = customerServiceEntityRepository
                            .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                            .orElse(null);
                    if (service == null || defaultAmount(service.getMonthlyFee()).signum() <= 0) {
                        return;
                    }
                    if ("terminated".equalsIgnoreCase(service.getStatus())) {
                        return;
                    }

                    LocalDate latestDueDate = invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customer.getId()).stream()
                            .filter(invoice -> !"cancelled".equalsIgnoreCase(invoice.getStatus()))
                            .map(invoice -> invoice.getDueDate() != null ? invoice.getDueDate() : invoice.getBillingMonth())
                            .filter(Objects::nonNull)
                            .max(LocalDate::compareTo)
                            .orElse(resolveStartingDueDate(customer, service));

                    LocalDate nextDueDate = latestDueDate.plusMonths(1);
                    boolean createdAny = false;
                    while (!YearMonth.from(nextDueDate).isAfter(currentMonth)) {
                        if (invoiceRepository.findByCustomerCodeAndBillingMonth(customer.getCustomerCode(), nextDueDate).isEmpty()) {
                            Invoice created = createRecurringInvoice(customer, service, nextDueDate);
                            billingAuditTrailService.record(
                                    "INVOICE_GENERATE",
                                    "Invoice otomatis " + created.getInvoiceNumber() + " dibuat untuk pelanggan "
                                            + customer.getCustomerCode() + " periode " + nextDueDate + "."
                            );
                            generatedCount[0] += 1;
                            createdAny = true;
                        }
                        nextDueDate = nextDueDate.plusMonths(1);
                    }
                    if (!createdAny) {
                        return;
                    }
                });
        return generatedCount[0];
    }

    @Transactional
    public void autoSuspendOverdueCustomers() {
        LocalDate today = LocalDate.now();
        invoiceRepository.findAll().stream()
                .filter(invoice -> invoice.getCustomer() != null)
                .filter(invoice -> invoice.getDueDate() != null && invoice.getDueDate().isBefore(today))
                .filter(invoice -> !"paid".equalsIgnoreCase(invoice.getStatus()))
                .filter(invoice -> !"cancelled".equalsIgnoreCase(invoice.getStatus()))
                .filter(invoice -> !INVOICE_STATUS_NO_PAYMENT.equalsIgnoreCase(invoice.getStatus()))
                .map(Invoice::getCustomer)
                .distinct()
                .filter(customer -> !"suspended".equalsIgnoreCase(customer.getStatus()))
                .filter(customer -> !"terminated".equalsIgnoreCase(customer.getStatus()))
                .forEach(customer -> {
                    try {
                        suspendCustomer(customer.getId(), "Auto suspend karena invoice melewati jatuh tempo.");
                    } catch (Exception ex) {
                        log.warn("Failed to auto suspend customer {}: {}", customer.getCustomerCode(), ex.getMessage());
                    }
                });
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportCustomerInvoices(Long customerId, String status, String format) {
        Customer customer = getCustomer(customerId);
        List<InvoiceRowDTO> invoices = getCustomerInvoices(customerId, status);
        String normalizedFormat = normalizeFormat(format, "pdf");
        byte[] content = "xlsx".equals(normalizedFormat)
                ? renderInvoicesExcel(customer, invoices)
                : renderInvoicesPdf(customer, invoices);

        String fileName = "invoices-" + customer.getCustomerCode() + "." + normalizedFormat;
        MediaType mediaType = "xlsx".equals(normalizedFormat)
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.APPLICATION_PDF;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(mediaType)
                .body(content);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exportCustomerPayments(Long customerId, LocalDate startDate, LocalDate endDate) {
        Customer customer = getCustomer(customerId);
        List<PaymentHistoryItemDTO> payments = getCustomerPayments(customerId, startDate, endDate);
        byte[] content = renderPaymentsPdf(customer, payments);
        String fileName = "payment-history-" + customer.getCustomerCode() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }

    private void mirrorPaymentHistory(Invoice invoice, InvoiceRowDTO invoiceRow, LocalDate paymentDate, String paymentMethod) {
        if (!tableExists("payment_history")) {
            return;
        }
        try {
            if (paymentHistoryRepository.existsPositiveEntryByInvoiceId(invoice.getId())) {
                return;
            }

            PaymentHistoryEntry entry = new PaymentHistoryEntry();
            entry.setCustomer(invoice.getCustomer());
            entry.setInvoice(invoice);
            entry.setAmount(defaultAmount(invoiceRow.getTotalAmount()));
            entry.setPaymentDate(paymentDate);
            entry.setMethod(normalizePaymentMethod(paymentMethod));
            entry.setDescription(resolvePaymentDescription(invoice));
            paymentHistoryRepository.save(entry);
        } catch (DataAccessException ex) {
            if (!isMissingRelation(ex, "payment_history")) {
                throw ex;
            }
            log.warn("Skipping payment_history mirror for invoice {} because the table is unavailable.", invoice.getId());
        }
    }

    private void markMissedPaymentsAtMonthEnd(LocalDate today) {
        invoiceRepository.findAll().stream()
                .filter(invoice -> invoice.getCustomer() != null)
                .filter(invoice -> invoice.getId() != null)
                .filter(invoice -> !"paid".equalsIgnoreCase(invoice.getStatus()) && !"cancelled".equalsIgnoreCase(invoice.getStatus()))
                .filter(invoice -> !INVOICE_STATUS_NO_PAYMENT.equalsIgnoreCase(invoice.getStatus()))
                .filter(invoice -> isPastDueMonth(invoice, today))
                .forEach(this::createNoPaymentHistoryIfNeeded);
    }

    private boolean isPastDueMonth(Invoice invoice, LocalDate today) {
        LocalDate dueDate = invoice.getDueDate() != null ? invoice.getDueDate() : invoice.getBillingMonth();
        if (dueDate == null || today == null) {
            return false;
        }
        return dueDate.withDayOfMonth(dueDate.lengthOfMonth()).isBefore(today);
    }

    private void createNoPaymentHistoryIfNeeded(Invoice invoice) {
        if (!tableExists("payment_history")) {
            markInvoiceAsNoPayment(invoice);
            return;
        }
        try {
            if (paymentHistoryRepository.existsPositiveEntryByInvoiceId(invoice.getId())) {
                return;
            }
            if (paymentHistoryRepository.existsByInvoiceIdAndDescription(invoice.getId(), HISTORY_DESCRIPTION_NO_PAYMENT)) {
                return;
            }
        } catch (DataAccessException ex) {
            if (!isMissingRelation(ex, "payment_history")) {
                throw ex;
            }
            log.warn("payment_history table is unavailable while closing invoice {} as no payment. Continuing with invoice status only.", invoice.getId());
            markInvoiceAsNoPayment(invoice);
            return;
        }

        LocalDate dueDate = invoice.getDueDate() != null ? invoice.getDueDate() : invoice.getBillingMonth();
        if (dueDate == null) {
            return;
        }

        PaymentHistoryEntry entry = new PaymentHistoryEntry();
        entry.setCustomer(invoice.getCustomer());
        entry.setInvoice(invoice);
        entry.setAmount(BigDecimal.ZERO);
        entry.setPaymentDate(dueDate.withDayOfMonth(dueDate.lengthOfMonth()));
        entry.setMethod(HISTORY_METHOD_SYSTEM);
        entry.setDescription(HISTORY_DESCRIPTION_NO_PAYMENT);
        paymentHistoryRepository.save(entry);
        markInvoiceAsNoPayment(invoice);
    }

    private void markInvoiceAsNoPayment(Invoice invoice) {
        invoice.setStatus(INVOICE_STATUS_NO_PAYMENT);
        invoice.setPaymentNotes("Invoice ditutup otomatis sebagai Tidak Bayar pada akhir bulan berjalan.");
        invoiceRepository.save(invoice);

        billingAuditTrailService.record(
                "PAYMENT_HISTORY",
                "History tidak bayar otomatis dicatat untuk invoice " + invoice.getInvoiceNumber()
                        + " pelanggan " + invoice.getCustomer().getCustomerCode() + "."
        );
    }

    private void reactivateCustomerIfNeeded(Invoice invoice, LocalDate paymentDate, boolean reactivationAfterNoPayment) {
        Customer customer = invoice.getCustomer();
        if (customer == null || "terminated".equalsIgnoreCase(customer.getStatus())) {
            return;
        }
        if (!"suspended".equalsIgnoreCase(customer.getStatus())) {
            return;
        }

        CustomerServiceEntity service = invoice.getCustomerService();
        if (service == null) {
            service = customerServiceEntityRepository.findTopByCustomerIdOrderByCreatedAtDesc(customer.getId()).orElse(null);
        }

        try {
            billingPppoeAutomationService.enableCustomer(customer.getId(), null, null, "Aktivasi ulang setelah pembayaran", "manual");
        } catch (Exception ex) {
            log.warn("Failed to enable PPPoE for customer {}: {}", customer.getCustomerCode(), ex.getMessage());
        }
        customer.setStatus("active");
        customer.setIsActive(true);
        customerRepository.save(customer);
        if (service != null) {
            service.setStatus("active");
            if (reactivationAfterNoPayment && paymentDate != null) {
                service.setActivationDate(paymentDate);
            }
            customerServiceEntityRepository.save(service);
        }
    }

    private Invoice createRecurringInvoice(Customer customer, CustomerServiceEntity service, LocalDate dueDate) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invoiceNumberService.generate());
        invoice.setCustomer(customer);
        invoice.setCustomerService(service);
        billingInvoiceService.applyCompanyContext(invoice, service);
        invoice.setBillingMonth(dueDate);
        invoice.setDueDate(dueDate);
        invoice.setMonthlyFee(defaultAmount(service.getMonthlyFee()));
        invoice.setInstallationFee(BigDecimal.ZERO);
        invoice.setOtherCharges(BigDecimal.ZERO);
        invoice.setTotalAmount(defaultAmount(service.getMonthlyFee()));
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setInvoiceType("subscription");
        invoice.setStatus(dueDate.isBefore(LocalDate.now()) ? "overdue" : "pending");
        invoice.setNotes("Tagihan rutin otomatis.");
        invoice.setPaymentDate(null);
        invoice.setPaymentMethod(null);
        return invoiceRepository.save(invoice);
    }

    private LocalDate resolveStartingDueDate(Customer customer, CustomerServiceEntity service) {
        if (service != null && service.getActivationDate() != null) {
            return service.getActivationDate();
        }
        if (customer.getRegistrationDate() != null) {
            return customer.getRegistrationDate();
        }
        return LocalDate.now();
    }

    private boolean isOutstanding(InvoiceRowDTO row) {
        if (row == null) {
            return false;
        }
        String status = row.getStatus() != null ? row.getStatus().toLowerCase(Locale.ROOT) : "";
        return "pending".equals(status) || "overdue".equals(status) || "partial".equals(status) || "unpaid".equals(status);
    }

    private boolean hasPreviousCalendarMonthNoPayment(Invoice invoice, LocalDate paymentDate) {
        if (invoice == null || invoice.getCustomer() == null || invoice.getCustomer().getId() == null || paymentDate == null) {
            return false;
        }

        YearMonth previousMonth = YearMonth.from(paymentDate.minusMonths(1));
        LocalDate startDate = previousMonth.atDay(1);
        LocalDate endDate = previousMonth.atEndOfMonth();
        if (!tableExists("payment_history")) {
            return hasPreviousCalendarMonthNoPaymentFromInvoices(invoice.getCustomer().getId(), previousMonth);
        }
        try {
            return paymentHistoryRepository.existsByCustomerIdAndDescriptionAndPaymentDateBetween(
                    invoice.getCustomer().getId(),
                    HISTORY_DESCRIPTION_NO_PAYMENT,
                    startDate,
                    endDate
            );
        } catch (DataAccessException ex) {
            if (!isMissingRelation(ex, "payment_history")) {
                throw ex;
            }
            log.warn("payment_history table is unavailable while checking previous no-payment state for customer {}. Falling back to invoice status.", invoice.getCustomer().getId());
            return hasPreviousCalendarMonthNoPaymentFromInvoices(invoice.getCustomer().getId(), previousMonth);
        }
    }

    private boolean matchesInvoiceStatus(InvoiceRowDTO row, String normalizedStatus) {
        if (normalizedStatus == null) {
            return true;
        }
        String status = row.getStatus() != null ? row.getStatus().toLowerCase(Locale.ROOT) : "";
        return switch (normalizedStatus) {
            case "unpaid" -> "pending".equals(status) || "overdue".equals(status) || "partial".equals(status) || "unpaid".equals(status);
            default -> normalizedStatus.equals(status);
        };
    }

    private String normalizeInvoiceStatusFilter(String status) {
        if (!StringUtils.hasText(status) || "all".equalsIgnoreCase(status)) {
            return null;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "unpaid", "pending", "overdue", "paid" -> normalized;
            default -> throw new IllegalArgumentException("Status invoice tidak valid.");
        };
    }

    private String normalizePaymentMethod(String paymentMethod) {
        String normalized = paymentMethod != null ? paymentMethod.trim().toUpperCase(Locale.ROOT) : "";
        return "TRANSFER".equals(normalized) ? "TRANSFER" : "CASH";
    }

    private String resolvePaymentDescription(Invoice invoice) {
        if (invoice != null && "activation".equalsIgnoreCase(invoice.getInvoiceType())) {
            return HISTORY_DESCRIPTION_ACTIVATION;
        }
        return HISTORY_DESCRIPTION_MONTHLY;
    }

    private PaymentHistoryItemDTO toPaymentHistoryItem(PaymentHistoryEntry entry) {
        return new PaymentHistoryItemDTO(
                entry.getId(),
                entry.getCustomer() != null ? entry.getCustomer().getId() : null,
                entry.getInvoice() != null ? entry.getInvoice().getId() : null,
                entry.getInvoice() != null ? entry.getInvoice().getInvoiceNumber() : null,
                entry.getPaymentDate(),
                entry.getAmount(),
                entry.getMethod(),
                entry.getDescription()
        );
    }

    private PaymentHistoryItemDTO toPaymentHistoryItem(Payment payment) {
        Invoice invoice = payment != null ? payment.getInvoice() : null;
        return new PaymentHistoryItemDTO(
                payment != null ? payment.getId() : null,
                payment != null && payment.getCustomer() != null ? payment.getCustomer().getId() : null,
                invoice != null ? invoice.getId() : null,
                invoice != null ? invoice.getInvoiceNumber() : null,
                payment != null ? payment.getPaymentDate() : null,
                payment != null ? payment.getAmountPaid() : BigDecimal.ZERO,
                payment != null ? payment.getPaymentMethod() : null,
                resolvePaymentDescription(invoice)
        );
    }

    private Customer getCustomer(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Pelanggan tidak ditemukan."));
    }

    private void triggerAutomaticReceiptDelivery(Long invoiceId) {
        if (!whatsappGatewayService.isBotActive()) {
            return;
        }
        Runnable task = () -> {
            try {
                whatsappReminderAutomationService.sendAutomaticPaymentReceipt(invoiceId);
            } catch (Exception ex) {
                log.warn("Automatic payment receipt delivery failed for invoice {}: {}", invoiceId, ex.getMessage());
                billingAuditTrailService.record(
                        "WHATSAPP_RECEIPT_SEND_AUTO_FAILED",
                        "Pengiriman otomatis struk pembayaran untuk invoice " + invoiceId + " gagal: " + ex.getMessage()
                );
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void schedulePostPaymentActions(Long invoiceId,
                                            InvoiceRowDTO paidInvoice,
                                            LocalDate paymentDate,
                                            String paymentMethod) {
        Runnable task = () -> runPostPaymentActions(invoiceId, paidInvoice, paymentDate, paymentMethod);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void runPostPaymentActions(Long invoiceId,
                                       InvoiceRowDTO paidInvoice,
                                       LocalDate paymentDate,
                                       String paymentMethod) {
        try {
            Invoice invoice = billingInvoiceService.getInvoiceEntityById(invoiceId);
            boolean reactivationAfterNoPayment = hasPreviousCalendarMonthNoPayment(invoice, paymentDate);
            mirrorPaymentHistory(invoice, paidInvoice, paymentDate, paymentMethod);
            reactivateCustomerIfNeeded(invoice, paymentDate, reactivationAfterNoPayment);
            billingAuditTrailService.record(
                    "PAYMENT",
                    "Pembayaran invoice " + paidInvoice.getInvoiceNumber() + " untuk pelanggan "
                            + paidInvoice.getCustomerCode() + " berhasil dicatat."
            );
            triggerAutomaticReceiptDelivery(invoiceId);
        } catch (Exception ex) {
            log.warn("Post-payment actions failed for invoice {}: {}", invoiceId, ex.getMessage(), ex);
            billingAuditTrailService.record(
                    "PAYMENT_POST_PROCESS_FAILED",
                    "Tindak lanjut pembayaran invoice " + invoiceId + " gagal: " + ex.getMessage()
            );
        }
    }

    private boolean matchesPaymentDateRange(Payment payment, LocalDate startDate, LocalDate endDate) {
        if (payment == null || payment.getPaymentDate() == null) {
            return false;
        }
        if (startDate != null && payment.getPaymentDate().isBefore(startDate)) {
            return false;
        }
        return endDate == null || !payment.getPaymentDate().isAfter(endDate);
    }

    private boolean hasPreviousCalendarMonthNoPaymentFromInvoices(Long customerId, YearMonth previousMonth) {
        if (customerId == null || previousMonth == null) {
            return false;
        }
        return invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customerId).stream()
                .filter(invoice -> isNoPaymentStatus(invoice.getStatus()))
                .map(invoice -> invoice.getDueDate() != null ? invoice.getDueDate() : invoice.getBillingMonth())
                .filter(Objects::nonNull)
                .map(YearMonth::from)
                .anyMatch(previousMonth::equals);
    }

    private boolean isNoPaymentStatus(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return INVOICE_STATUS_NO_PAYMENT.equals(normalized)
                || "no-payment".equals(normalized)
                || "tidak_bayar".equals(normalized);
    }

    private boolean isMissingRelation(Throwable throwable, String relationName) {
        String normalizedRelation = relationName != null ? relationName.toLowerCase(Locale.ROOT) : "";
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("relation \"" + normalizedRelation + "\" does not exist")
                        || normalized.contains("table \"" + normalizedRelation + "\" does not exist")
                        || normalized.contains(normalizedRelation + " does not exist")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean tableExists(String tableName) {
        try {
            String relation = jdbcTemplate.queryForObject("select to_regclass(?)", String.class, tableName);
            return relation != null && !relation.isBlank();
        } catch (DataAccessException ex) {
            log.warn("Unable to verify relation {} existence. Assuming unavailable for safety.", tableName, ex);
            return false;
        }
    }

    private void releaseServiceUniqueFields(CustomerServiceEntity service, Customer customer) {
        if (service == null) {
            return;
        }
        String archiveSuffix = buildArchiveSuffix(customer != null ? customer.getId() : service.getId());
        if (StringUtils.hasText(service.getPppoeUsername())) {
            service.setPppoeUsername(truncate(service.getPppoeUsername() + archiveSuffix, 50));
        }
        if (StringUtils.hasText(service.getOntSerial())) {
            service.setOntSerial(truncate(service.getOntSerial() + archiveSuffix, 50));
        }
    }

    private void releaseCustomerUniqueFields(Customer customer) {
        if (customer == null) {
            return;
        }
        String archiveSuffix = buildArchiveSuffix(customer.getId());
        if (StringUtils.hasText(customer.getKtpNumber())) {
            customer.setKtpNumber(truncate(customer.getKtpNumber() + archiveSuffix, 20));
        }
        if (StringUtils.hasText(customer.getCustomerCode())) {
            customer.setCustomerCode(truncate(customer.getCustomerCode() + archiveSuffix, 20));
        }
    }

    private String buildArchiveSuffix(Long referenceId) {
        return "-DEL" + (referenceId != null ? referenceId : System.currentTimeMillis());
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private byte[] renderInvoicesPdf(Customer customer, List<InvoiceRowDTO> invoices) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(new Paragraph("Daftar Invoice Pelanggan", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            document.add(new Paragraph(customer.getFullName() + " (" + customer.getCustomerCode() + ")"));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            addHeader(table, "Invoice");
            addHeader(table, "Jatuh Tempo");
            addHeader(table, "Status");
            addHeader(table, "Total");
            addHeader(table, "Terbayar");
            for (InvoiceRowDTO invoice : invoices) {
                table.addCell(text(invoice.getInvoiceNumber()));
                table.addCell(text(invoice.getDueDate() != null ? invoice.getDueDate().toString() : ""));
                table.addCell(text(String.valueOf(invoice.getStatus())));
                table.addCell(text(defaultAmount(invoice.getTotalAmount()).toPlainString()));
                table.addCell(text(defaultAmount(invoice.getAmountPaid()).toPlainString()));
            }
            document.add(table);
            document.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Gagal membuat PDF invoice.", ex);
        }
    }

    private byte[] renderInvoicesExcel(Customer customer, List<InvoiceRowDTO> invoices) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Invoices");
            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue("Customer");
            titleRow.createCell(1).setCellValue(customer.getFullName() + " (" + customer.getCustomerCode() + ")");

            Row header = sheet.createRow(2);
            header.createCell(0).setCellValue("Invoice");
            header.createCell(1).setCellValue("Due Date");
            header.createCell(2).setCellValue("Status");
            header.createCell(3).setCellValue("Total");
            header.createCell(4).setCellValue("Paid");

            int rowIndex = 3;
            for (InvoiceRowDTO invoice : invoices) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(invoice.getInvoiceNumber());
                row.createCell(1).setCellValue(invoice.getDueDate() != null ? invoice.getDueDate().toString() : "");
                row.createCell(2).setCellValue(invoice.getStatus());
                row.createCell(3).setCellValue(defaultAmount(invoice.getTotalAmount()).doubleValue());
                row.createCell(4).setCellValue(defaultAmount(invoice.getAmountPaid()).doubleValue());
            }

            for (int i = 0; i < 5; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Gagal membuat file Excel invoice.", ex);
        }
    }

    private byte[] renderPaymentsPdf(Customer customer, List<PaymentHistoryItemDTO> payments) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(new Paragraph("History Pembayaran", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            document.add(new Paragraph(customer.getFullName() + " (" + customer.getCustomerCode() + ")"));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            addHeader(table, "Tanggal");
            addHeader(table, "Jumlah");
            addHeader(table, "Metode");
            addHeader(table, "Deskripsi");

            BigDecimal total = BigDecimal.ZERO;
            for (PaymentHistoryItemDTO payment : payments) {
                table.addCell(text(payment.getPaymentDate() != null ? payment.getPaymentDate().toString() : ""));
                table.addCell(text(defaultAmount(payment.getAmount()).toPlainString()));
                table.addCell(text(payment.getMethod()));
                table.addCell(text(payment.getDescription()));
                total = total.add(defaultAmount(payment.getAmount()));
            }

            document.add(table);
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Total pembayaran: " + total.toPlainString(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
            document.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Gagal membuat PDF history pembayaran.", ex);
        }
    }

    private void addHeader(PdfPTable table, String title) {
        table.addCell(new PdfPCell(new Phrase(title)));
    }

    private PdfPCell text(String value) {
        return new PdfPCell(new Phrase(value != null ? value : ""));
    }

    private String normalizeFormat(String format, String defaultFormat) {
        if (!StringUtils.hasText(format)) {
            return defaultFormat;
        }
        String normalized = format.trim().toLowerCase(Locale.ROOT);
        return "xlsx".equals(normalized) ? "xlsx" : defaultFormat;
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
