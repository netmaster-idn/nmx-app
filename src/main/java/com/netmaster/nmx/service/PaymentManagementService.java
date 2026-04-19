package com.netmaster.nmx.service;

import com.netmaster.nmx.dto.HistoryPaymentRowDTO;
import com.netmaster.nmx.dto.InvoiceRowDTO;
import com.netmaster.nmx.dto.PaymentRecordDTO;
import com.netmaster.nmx.dto.RecordPaymentRequest;
import com.netmaster.nmx.model.Customer;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.model.Invoice;
import com.netmaster.nmx.model.Payment;
import com.netmaster.nmx.repository.PaymentHistoryRepository;
import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import com.netmaster.nmx.repository.InvoiceRepository;
import com.netmaster.nmx.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentManagementService {

    private static final long PAYMENT_REMINDER_WINDOW_DAYS = 3L;
    private static final String AUTO_NEXT_INVOICE_NOTE = "Tagihan rutin otomatis setelah pembayaran.";

    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final CustomerServiceEntityRepository customerServiceEntityRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final BillingInvoiceService billingInvoiceService;
    private final InvoiceNumberService invoiceNumberService;
    private final BillingAutomationSettingsService billingAutomationSettingsService;
    private final BillingSchedulerService billingSchedulerService;
    private final WhatsappGatewayService whatsappGatewayService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public InvoiceRowDTO recordPayment(Long invoiceId, RecordPaymentRequest request) {
        ensurePaymentHistoryTableExists();
        Invoice invoice = billingInvoiceService.getInvoiceEntityById(invoiceId);
        InvoiceRowDTO currentInvoice = billingInvoiceService.getInvoiceRowById(invoiceId);

        if ("no_payment".equalsIgnoreCase(currentInvoice.getStatus())
                || "no-payment".equalsIgnoreCase(currentInvoice.getStatus())
                || "tidak_bayar".equalsIgnoreCase(currentInvoice.getStatus())) {
            throw new RuntimeException("Invoice sudah ditutup sebagai Tidak Bayar dan hanya disimpan sebagai histori.");
        }
        if ("paid".equalsIgnoreCase(currentInvoice.getStatus())) {
            throw new RuntimeException("Invoice sudah lunas!");
        }
        if ("cancelled".equalsIgnoreCase(currentInvoice.getStatus())) {
            throw new RuntimeException("Invoice sudah dibatalkan!");
        }
        if (paymentRepository.countByInvoiceId(invoiceId) > 0) {
            throw new RuntimeException("Pembayaran ganda untuk invoice yang sama tidak diizinkan!");
        }

        BigDecimal outstandingAmount = currentInvoice.getOutstandingAmount() != null
                ? currentInvoice.getOutstandingAmount()
                : BigDecimal.ZERO;
        if (request.getAmount().compareTo(outstandingAmount) != 0) {
            throw new RuntimeException("Pembayaran harus melunasi seluruh sisa tagihan.");
        }

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setCustomer(invoice.getCustomer());
        payment.setAmountPaid(request.getAmount());
        payment.setPaymentDate(request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now());
        payment.setPaymentMethod(normalizePaymentMethod(request.getPaymentMethod()));
        payment.setReferenceNumber(trimToNull(request.getReferenceNumber()));
        payment.setNotes(trimToNull(request.getNotes()));
        paymentRepository.save(payment);

        billingInvoiceService.refreshInvoiceSnapshot(invoice);
        ensureNextInvoiceAfterPayment(invoice, payment.getPaymentDate());
        ensureActivationInvoicePaidFromFirstPayment(invoice.getCustomer() != null ? invoice.getCustomer().getId() : null);
        dispatchReceiptImmediatelyIfEnabled(payment);
        return billingInvoiceService.getInvoiceRowById(invoiceId);
    }

    @Transactional
    public Invoice ensureNextInvoiceAfterPayment(Invoice paidInvoice, LocalDate paymentDate) {
        if (paidInvoice == null) {
            return null;
        }
        if (!"subscription".equalsIgnoreCase(paidInvoice.getInvoiceType())) {
            return null;
        }

        Customer customer = paidInvoice.getCustomer();
        if (customer == null || customer.getId() == null) {
            return null;
        }

        CustomerServiceEntity service = paidInvoice.getCustomerService();
        if (service == null) {
            service = customerServiceEntityRepository.findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                    .orElse(null);
        }
        if (service == null || defaultAmount(service.getMonthlyFee()).signum() <= 0) {
            return null;
        }
        CustomerServiceEntity resolvedService = service;

        LocalDate nextDueDate = resolveNextRecurringDueDate(paidInvoice, paymentDate);
        if (nextDueDate == null) {
            return null;
        }

        return invoiceRepository.findByCustomerCodeAndBillingMonth(customer.getCustomerCode(), nextDueDate)
                .orElseGet(() -> {
                    Invoice scheduledInvoice = findUpcomingAdjustableInvoice(customer.getId(), paidInvoice.getId(), nextDueDate);
                    if (scheduledInvoice != null) {
                        scheduledInvoice.setCustomerService(resolvedService);
                        billingInvoiceService.applyCompanyContext(scheduledInvoice, resolvedService);
                        scheduledInvoice.setBillingMonth(nextDueDate);
                        scheduledInvoice.setDueDate(nextDueDate);
                        scheduledInvoice.setMonthlyFee(defaultAmount(resolvedService.getMonthlyFee()));
                        scheduledInvoice.setInstallationFee(BigDecimal.ZERO);
                        scheduledInvoice.setOtherCharges(BigDecimal.ZERO);
                        scheduledInvoice.setTotalAmount(defaultAmount(resolvedService.getMonthlyFee()));
                        scheduledInvoice.setAmountPaid(BigDecimal.ZERO);
                        scheduledInvoice.setInvoiceType("subscription");
                        scheduledInvoice.setStatus(nextDueDate.isBefore(LocalDate.now()) ? "overdue" : "pending");
                        if (!StringUtils.hasText(scheduledInvoice.getNotes())) {
                            scheduledInvoice.setNotes("Tagihan rutin otomatis setelah pembayaran.");
                        }
                        return invoiceRepository.save(scheduledInvoice);
                    }

                    Invoice nextInvoice = new Invoice();
                    nextInvoice.setInvoiceNumber(invoiceNumberService.generate());
                    nextInvoice.setCustomer(customer);
                    nextInvoice.setCustomerService(resolvedService);
                    billingInvoiceService.applyCompanyContext(nextInvoice, resolvedService);
                    nextInvoice.setBillingMonth(nextDueDate);
                    nextInvoice.setDueDate(nextDueDate);
                    nextInvoice.setMonthlyFee(defaultAmount(resolvedService.getMonthlyFee()));
                    nextInvoice.setInstallationFee(BigDecimal.ZERO);
                    nextInvoice.setOtherCharges(BigDecimal.ZERO);
                    nextInvoice.setTotalAmount(defaultAmount(resolvedService.getMonthlyFee()));
                    nextInvoice.setAmountPaid(BigDecimal.ZERO);
                    nextInvoice.setStatus(nextDueDate.isBefore(LocalDate.now()) ? "overdue" : "pending");
                    nextInvoice.setInvoiceType("subscription");
                    nextInvoice.setNotes("Tagihan rutin otomatis setelah pembayaran.");
                    return invoiceRepository.save(nextInvoice);
                });
    }

    @Transactional(readOnly = true)
    public List<PaymentRecordDTO> getPaymentsByCustomer(Long customerId, Integer year, Integer month) {
        return paymentRepository.findByCustomerIdOrderByPaymentDateDescIdDesc(customerId).stream()
                .filter(payment -> matchesPaymentPeriod(payment, year, month))
                .map(this::toPaymentRecord)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PaymentRecordDTO> getPaymentsByInvoice(Long invoiceId) {
        return paymentRepository.findByInvoiceIdOrderByPaymentDateDescIdDesc(invoiceId).stream()
                .map(this::toPaymentRecord)
                .toList();
    }

    @Transactional
    public InvoiceRowDTO deletePaymentsByInvoice(Long invoiceId) {
        ensurePaymentHistoryTableExists();
        Invoice invoice = billingInvoiceService.getInvoiceEntityById(invoiceId);
        List<Payment> payments = paymentRepository.findByInvoiceIdOrderByPaymentDateDescIdDesc(invoiceId);

        boolean hasLegacySnapshotPayment = invoice.getAmountPaid() != null
                && invoice.getAmountPaid().compareTo(BigDecimal.ZERO) > 0;
        if (!hasLegacySnapshotPayment) {
            hasLegacySnapshotPayment = invoice.getPaymentDate() != null
                    || StringUtils.hasText(invoice.getPaymentMethod())
                    || StringUtils.hasText(invoice.getPaymentNotes());
        }

        int deletedHistoryEntries = 0;
        if (tableExists("payment_history")) {
            try {
                deletedHistoryEntries = paymentHistoryRepository.deleteByInvoiceId(invoiceId);
            } catch (DataAccessException ex) {
                if (!isMissingPaymentHistoryTable(ex)) {
                    throw ex;
                }
            }
        }

        if (payments.isEmpty() && deletedHistoryEntries == 0 && !hasLegacySnapshotPayment) {
            throw new RuntimeException("Belum ada data pembayaran yang dapat dihapus untuk invoice ini.");
        }

        if (!payments.isEmpty()) {
            paymentRepository.deleteAll(payments);
        }

        deleteAutoGeneratedNextInvoiceAfterPaymentRollback(invoice, payments);

        billingInvoiceService.refreshInvoiceSnapshot(invoice);
        return billingInvoiceService.getInvoiceRowById(invoiceId);
    }

    private boolean isMissingPaymentHistoryTable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("payment_history") && normalized.contains("does not exist")) {
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
            return false;
        }
    }

    private void ensurePaymentHistoryTableExists() {
        if (tableExists("payment_history")) {
            return;
        }
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS payment_history (
                        id BIGSERIAL PRIMARY KEY,
                        customer_id BIGINT NOT NULL REFERENCES customers(id),
                        invoice_id BIGINT NOT NULL REFERENCES invoices(id),
                        amount DECIMAL(12,2) NOT NULL,
                        payment_date DATE NOT NULL,
                        method VARCHAR(20) NOT NULL,
                        description VARCHAR(30) NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT uk_payment_history_invoice UNIQUE (invoice_id)
                    )
                    """);
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_payment_history_customer ON payment_history(customer_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_payment_history_invoice ON payment_history(invoice_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_payment_history_payment_date ON payment_history(payment_date)");
            jdbcTemplate.execute("""
                    INSERT INTO payment_history (customer_id, invoice_id, amount, payment_date, method, description, created_at)
                    SELECT p.customer_id,
                           p.invoice_id,
                           SUM(p.amount_paid) AS amount,
                           MAX(p.payment_date) AS payment_date,
                           COALESCE(MAX(NULLIF(TRIM(p.payment_method), '')), 'CASH') AS method,
                           CASE
                               WHEN LOWER(COALESCE(MAX(i.invoice_type), 'subscription')) = 'activation' THEN 'AKTIVASI'
                               ELSE 'BULANAN'
                           END AS description,
                           COALESCE(MAX(p.created_at), CURRENT_TIMESTAMP) AS created_at
                    FROM payments p
                    JOIN invoices i ON i.id = p.invoice_id
                    LEFT JOIN payment_history ph ON ph.invoice_id = p.invoice_id
                    WHERE ph.id IS NULL
                    GROUP BY p.customer_id, p.invoice_id
                    """);
        } catch (DataAccessException ignored) {
            // Keep business flow running even when schema sync is unavailable.
        }
    }

    private void deleteAutoGeneratedNextInvoiceAfterPaymentRollback(Invoice paidInvoice, List<Payment> deletedPayments) {
        if (paidInvoice == null || paidInvoice.getId() == null || paidInvoice.getCustomer() == null) {
            return;
        }
        if (!"subscription".equalsIgnoreCase(paidInvoice.getInvoiceType())) {
            return;
        }

        LocalDate latestPaymentDate = deletedPayments == null
                ? null
                : deletedPayments.stream()
                .map(Payment::getPaymentDate)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        if (latestPaymentDate == null) {
            latestPaymentDate = paidInvoice.getPaymentDate();
        }

        LocalDate nextDueDate = resolveNextRecurringDueDate(paidInvoice, latestPaymentDate);
        if (nextDueDate == null) {
            return;
        }

        Long customerId = paidInvoice.getCustomer().getId();
        if (customerId == null) {
            return;
        }

        List<Invoice> candidates = invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customerId).stream()
                .filter(invoice -> !Objects.equals(invoice.getId(), paidInvoice.getId()))
                .filter(invoice -> "subscription".equalsIgnoreCase(invoice.getInvoiceType()))
                .filter(invoice -> {
                    LocalDate scheduledDate = invoice.getDueDate() != null ? invoice.getDueDate() : invoice.getBillingMonth();
                    return Objects.equals(scheduledDate, nextDueDate);
                })
                .filter(invoice -> paymentRepository.countByInvoiceId(invoice.getId()) == 0)
                .filter(invoice -> defaultAmount(invoice.getAmountPaid()).compareTo(BigDecimal.ZERO) == 0)
                .filter(invoice -> {
                    String status = String.valueOf(invoice.getStatus()).toLowerCase(Locale.ROOT);
                    return !"paid".equals(status) && !"cancelled".equals(status) && !"no_payment".equals(status);
                })
                .filter(invoice -> StringUtils.hasText(invoice.getNotes())
                        && invoice.getNotes().toLowerCase(Locale.ROOT).contains(AUTO_NEXT_INVOICE_NOTE.toLowerCase(Locale.ROOT)))
                .toList();

        if (candidates.isEmpty()) {
            return;
        }

        invoiceRepository.deleteAll(candidates);
    }

    @Transactional(readOnly = true)
    public List<Integer> getDistinctYearsByCustomer(Long customerId) {
        return paymentRepository.findByCustomerIdOrderByPaymentDateDescIdDesc(customerId).stream()
                .map(Payment::getPaymentDate)
                .filter(Objects::nonNull)
                .map(LocalDate::getYear)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Integer> getDistinctMonthsByCustomerAndYear(Long customerId, Integer year) {
        if (year == null) {
            return List.of();
        }

        return paymentRepository.findByCustomerIdOrderByPaymentDateDescIdDesc(customerId).stream()
                .map(Payment::getPaymentDate)
                .filter(Objects::nonNull)
                .filter(paymentDate -> paymentDate.getYear() == year)
                .map(LocalDate::getMonthValue)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HistoryPaymentRowDTO> getCustomerHistoryRows(String statusFilter) {
        List<Customer> customers = customerRepository.findAll();
        if (customers.isEmpty()) {
            return List.of();
        }

        List<Long> customerIds = customers.stream()
                .map(Customer::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, CustomerServiceEntity> latestServiceByCustomer = customerServiceEntityRepository
                .findLatestWithInternetPackageByCustomerIds(customerIds).stream()
                .filter(service -> service.getCustomer() != null && service.getCustomer().getId() != null)
                .collect(Collectors.toMap(service -> service.getCustomer().getId(), service -> service, (left, right) -> left));

        Map<Long, List<InvoiceRowDTO>> invoicesByCustomer = billingInvoiceService.getInvoiceRowsByCustomerIds(customerIds).stream()
                .filter(invoice -> invoice.getCustomerId() != null)
                .collect(Collectors.groupingBy(InvoiceRowDTO::getCustomerId));

        String normalizedFilter = normalizeSummaryFilter(statusFilter);

        return customers.stream()
                .sorted(Comparator.comparing(customer -> customer.getFullName() != null ? customer.getFullName() : "", String.CASE_INSENSITIVE_ORDER))
                .map(customer -> {
                    List<InvoiceRowDTO> invoiceRows = invoicesByCustomer.getOrDefault(customer.getId(), List.of());
                    CustomerServiceEntity latestService = latestServiceByCustomer.get(customer.getId());
                    String paymentStatus = resolveCustomerPaymentStatus(invoiceRows);
                    BigDecimal totalTagihan = invoiceRows.stream()
                            .filter(this::isOutstandingInvoice)
                            .map(row -> row.getOutstandingAmount() != null ? row.getOutstandingAmount() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new HistoryPaymentRowDTO(
                            customer.getId(),
                            customer.getCustomerCode(),
                            customer.getFullName(),
                            customer.getPhone(),
                            latestService != null && latestService.getInternetPackage() != null
                                    ? latestService.getInternetPackage().getName()
                                    : null,
                            normalizeCustomerStatus(customer.getStatus()),
                            latestService != null ? latestService.getId() : null,
                            totalTagihan,
                            paymentStatus
                    );
                })
                .filter(row -> matchesSummaryFilter(row.getPaymentStatus(), normalizedFilter))
                .toList();
    }

    @Transactional
    public void backfillLegacyInvoicePayments() {
        List<InvoiceRowDTO> invoices = billingInvoiceService.getAllInvoiceRows();
        if (invoices.isEmpty()) {
            return;
        }

        Set<Long> invoiceIdsWithPayments = invoices.stream()
                .map(InvoiceRowDTO::getId)
                .filter(Objects::nonNull)
                .filter(invoiceId -> paymentRepository.countByInvoiceId(invoiceId) > 0)
                .collect(Collectors.toSet());

        invoices.stream()
                .filter(invoice -> invoice.getId() != null)
                .filter(invoice -> !invoiceIdsWithPayments.contains(invoice.getId()))
                .filter(invoice -> invoice.getAmountPaid() != null && invoice.getAmountPaid().signum() > 0)
                .forEach(invoice -> {
                    Invoice invoiceEntity = billingInvoiceService.getInvoiceEntityById(invoice.getId());
                    Payment payment = new Payment();
                    payment.setInvoice(invoiceEntity);
                    payment.setCustomer(invoiceEntity.getCustomer());
                    payment.setAmountPaid(invoice.getAmountPaid());
                    payment.setPaymentDate(invoice.getPaymentDate() != null ? invoice.getPaymentDate() : invoice.getDueDate() != null ? invoice.getDueDate() : LocalDate.now());
                    payment.setPaymentMethod(normalizePaymentMethod(invoice.getPaymentMethod()));
                    payment.setNotes(trimToNull(invoice.getPaymentNotes()));
                    paymentRepository.save(payment);
                    billingInvoiceService.refreshInvoiceSnapshot(invoiceEntity);
                });
    }

    @Transactional
    public void backfillActivationPaymentsFromFirstCustomerPayment() {
        customerRepository.findAll().stream()
                .map(Customer::getId)
                .filter(Objects::nonNull)
                .forEach(this::ensureActivationInvoicePaidFromFirstPayment);
    }

    @Transactional
    public void ensurePaymentRecordForInvoice(Invoice invoice, BigDecimal amount, LocalDate paymentDate, String paymentMethod, String notes) {
        if (invoice == null || invoice.getId() == null || amount == null || amount.signum() <= 0) {
            return;
        }
        if (paymentRepository.countByInvoiceId(invoice.getId()) > 0) {
            return;
        }

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setCustomer(invoice.getCustomer());
        payment.setAmountPaid(amount);
        payment.setPaymentDate(paymentDate != null ? paymentDate : LocalDate.now());
        payment.setPaymentMethod(normalizePaymentMethod(paymentMethod));
        payment.setNotes(trimToNull(notes));
        paymentRepository.save(payment);
        billingInvoiceService.refreshInvoiceSnapshot(invoice);
        dispatchReceiptImmediatelyIfEnabled(payment);
    }

    @Transactional
    public void ensureActivationInvoicePaidFromFirstPayment(Long customerId) {
        if (customerId == null) {
            return;
        }

        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null || !"active".equalsIgnoreCase(customer.getStatus())) {
            return;
        }

        CustomerServiceEntity service = customerServiceEntityRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customerId)
                .orElse(null);
        if (service == null || !"active".equalsIgnoreCase(service.getStatus())) {
            return;
        }

        Payment firstPayment = paymentRepository.findByCustomerIdOrderByPaymentDateAscIdAsc(customerId).stream()
                .filter(payment -> payment.getInvoice() != null)
                .filter(payment -> payment.getInvoice().getId() != null)
                .filter(payment -> !"activation".equalsIgnoreCase(payment.getInvoice().getInvoiceType()))
                .findFirst()
                .orElseGet(() -> paymentRepository.findByCustomerIdOrderByPaymentDateAscIdAsc(customerId).stream()
                        .filter(payment -> payment.getInvoice() != null && payment.getInvoice().getId() != null)
                        .findFirst()
                        .orElse(null));
        if (firstPayment == null) {
            return;
        }

        LocalDate activationDate = service.getActivationDate() != null
                ? service.getActivationDate()
                : firstPayment.getPaymentDate() != null ? firstPayment.getPaymentDate() : LocalDate.now();
        String paymentMethod = normalizePaymentMethod(firstPayment.getPaymentMethod());
        String paymentNotes = "Pembayaran aktivasi ditandai lunas otomatis dari pembayaran pertama pelanggan.";
        String invoiceNotes = "Invoice aktivasi pelanggan disinkronkan otomatis dari pembayaran pertama.";

        Invoice activationInvoice = invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customerId).stream()
                .filter(invoice -> "activation".equalsIgnoreCase(invoice.getInvoiceType()))
                .min(Comparator.comparing(Invoice::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Invoice::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        if (activationInvoice == null) {
            activationInvoice = new Invoice();
            activationInvoice.setInvoiceNumber(invoiceNumberService.generate());
            activationInvoice.setCustomer(customer);
            activationInvoice.setCustomerService(service);
            activationInvoice.setBillingMonth(activationDate.withDayOfMonth(1));
            activationInvoice.setDueDate(activationDate);
            activationInvoice.setMonthlyFee(BigDecimal.ZERO);
            activationInvoice.setInstallationFee(defaultAmount(service.getInstallationFee()));
            activationInvoice.setOtherCharges(BigDecimal.ZERO);
            activationInvoice.setTotalAmount(defaultAmount(service.getInstallationFee()));
            activationInvoice.setInvoiceType("activation");
            activationInvoice.setNotes(invoiceNotes);
        } else {
            activationInvoice.setCustomer(customer);
            activationInvoice.setCustomerService(service);
            if (activationInvoice.getBillingMonth() == null) {
                activationInvoice.setBillingMonth(activationDate.withDayOfMonth(1));
            }
            activationInvoice.setDueDate(activationDate);
            activationInvoice.setMonthlyFee(BigDecimal.ZERO);
            activationInvoice.setInstallationFee(defaultAmount(service.getInstallationFee()));
            activationInvoice.setOtherCharges(defaultAmount(activationInvoice.getOtherCharges()));
            activationInvoice.setTotalAmount(defaultAmount(service.getInstallationFee()).add(defaultAmount(activationInvoice.getOtherCharges())));
            activationInvoice.setInvoiceType("activation");
            if (!StringUtils.hasText(activationInvoice.getNotes())) {
                activationInvoice.setNotes(invoiceNotes);
            }
        }

        activationInvoice.setAmountPaid(defaultAmount(activationInvoice.getTotalAmount()));
        activationInvoice.setPaymentDate(firstPayment.getPaymentDate() != null ? firstPayment.getPaymentDate() : activationDate);
        activationInvoice.setPaymentMethod(paymentMethod);
        activationInvoice.setPaymentNotes(paymentNotes);
        activationInvoice.setStatus("paid");

        Invoice savedActivationInvoice = invoiceRepository.save(activationInvoice);
        billingInvoiceService.applyCompanyContext(savedActivationInvoice, service);
        savedActivationInvoice = invoiceRepository.save(savedActivationInvoice);
        ensurePaymentRecordForInvoice(
                savedActivationInvoice,
                defaultAmount(savedActivationInvoice.getTotalAmount()),
                savedActivationInvoice.getPaymentDate(),
                paymentMethod,
                paymentNotes
        );
    }

    private void dispatchReceiptImmediatelyIfEnabled(Payment payment) {
        if (payment == null || payment.getId() == null) {
            return;
        }
        if (!Boolean.TRUE.equals(billingAutomationSettingsService.getOrCreate().getAutoSendReceipt())) {
            return;
        }
        if (payment.getReceiptSentAt() != null) {
            return;
        }
        if (!whatsappGatewayService.isBotActive()) {
            return;
        }
        Runnable task = () -> {
            try {
                billingSchedulerService.sendReceipt(payment.getId());
            } catch (Exception ignored) {
                // Scheduler fallback will retry later if the gateway is temporarily unavailable.
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

    private PaymentRecordDTO toPaymentRecord(Payment payment) {
        InvoiceRowDTO invoice = billingInvoiceService.getInvoiceRowById(payment.getInvoice().getId());
        return new PaymentRecordDTO(
                payment.getId(),
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getCustomerId(),
                invoice.getCustomerCode(),
                invoice.getCustomerName(),
                invoice.getDueDate(),
                invoice.getTotalAmount(),
                payment.getAmountPaid(),
                invoice.getOutstandingAmount(),
                payment.getPaymentDate(),
                payment.getPaymentMethod(),
                payment.getReferenceNumber(),
                invoice.getStatus(),
                invoice.getInvoiceType(),
                invoice.getInvoiceTypeLabel(),
                payment.getNotes()
        );
    }

    private boolean matchesPaymentPeriod(Payment payment, Integer year, Integer month) {
        if (payment == null || payment.getPaymentDate() == null) {
            return false;
        }
        if (year != null && payment.getPaymentDate().getYear() != year) {
            return false;
        }
        return month == null || payment.getPaymentDate().getMonthValue() == month;
    }

    private boolean isOutstandingInvoice(InvoiceRowDTO invoice) {
        if (invoice == null) {
            return false;
        }
        if ("no_payment".equalsIgnoreCase(invoice.getStatus())
                || "no-payment".equalsIgnoreCase(invoice.getStatus())
                || "tidak_bayar".equalsIgnoreCase(invoice.getStatus())) {
            return false;
        }
        BigDecimal outstandingAmount = invoice.getOutstandingAmount() != null
                ? invoice.getOutstandingAmount()
                : BigDecimal.ZERO;
        if (outstandingAmount.signum() <= 0) {
            return false;
        }
        return !"paid".equalsIgnoreCase(invoice.getStatus()) && !"cancelled".equalsIgnoreCase(invoice.getStatus());
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private LocalDate resolveNextRecurringDueDate(Invoice paidInvoice, LocalDate paymentDate) {
        LocalDate dueDate = paidInvoice.getDueDate() != null ? paidInvoice.getDueDate() : paidInvoice.getBillingMonth();
        if (dueDate == null) {
            dueDate = paymentDate;
        }
        if (dueDate == null) {
            return null;
        }

        LocalDate anchorDate = paymentDate != null && paymentDate.isAfter(dueDate)
                ? paymentDate
                : dueDate;
        return anchorDate.plusMonths(1);
    }

    private Invoice findUpcomingAdjustableInvoice(Long customerId, Long paidInvoiceId, LocalDate nextDueDate) {
        if (customerId == null || nextDueDate == null) {
            return null;
        }

        return invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customerId).stream()
                .filter(invoice -> paidInvoiceId == null || !Objects.equals(invoice.getId(), paidInvoiceId))
                .filter(invoice -> "subscription".equalsIgnoreCase(invoice.getInvoiceType()))
                .filter(invoice -> !"paid".equalsIgnoreCase(invoice.getStatus()))
                .filter(invoice -> !"cancelled".equalsIgnoreCase(invoice.getStatus()))
                .filter(invoice -> !"no_payment".equalsIgnoreCase(invoice.getStatus()))
                .filter(invoice -> paymentRepository.countByInvoiceId(invoice.getId()) == 0)
                .filter(invoice -> {
                    LocalDate scheduledDate = invoice.getDueDate() != null ? invoice.getDueDate() : invoice.getBillingMonth();
                    return scheduledDate != null && !scheduledDate.isAfter(nextDueDate);
                })
                .min(Comparator.comparing(
                        (Invoice invoice) -> invoice.getDueDate() != null ? invoice.getDueDate() : invoice.getBillingMonth(),
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).thenComparing(Invoice::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private String resolveCustomerPaymentStatus(List<InvoiceRowDTO> invoices) {
        if (invoices == null || invoices.isEmpty()) {
            return "paid";
        }
        LocalDate today = LocalDate.now();
        boolean hasOverdue = invoices.stream()
                .filter(this::isOutstandingInvoice)
                .anyMatch(invoice -> isOverdueForCustomerStatus(invoice, today));
        if (hasOverdue) {
            return "overdue";
        }
        boolean hasUpcomingOutstanding = invoices.stream()
                .filter(this::isOutstandingInvoice)
                .anyMatch(invoice -> isWithinReminderWindow(invoice, today));
        if (hasUpcomingOutstanding) {
            return "unpaid";
        }
        boolean hasOutstanding = invoices.stream().anyMatch(this::isOutstandingInvoice);
        if (hasOutstanding) {
            return "unpaid";
        }
        boolean hasPaid = invoices.stream().anyMatch(invoice -> "paid".equalsIgnoreCase(invoice.getStatus()));
        return hasPaid ? "paid" : "unpaid";
    }

    private boolean isOverdueForCustomerStatus(InvoiceRowDTO invoice, LocalDate today) {
        if (invoice == null || today == null) {
            return false;
        }
        LocalDate dueDate = invoice.getDueDate();
        return dueDate != null && dueDate.isBefore(today);
    }

    private boolean isWithinReminderWindow(InvoiceRowDTO invoice, LocalDate today) {
        if (invoice == null || today == null) {
            return false;
        }
        LocalDate dueDate = invoice.getDueDate();
        if (dueDate == null) {
            return true;
        }
        return !today.isBefore(dueDate.minusDays(PAYMENT_REMINDER_WINDOW_DAYS)) && !dueDate.isBefore(today);
    }

    private boolean matchesSummaryFilter(String paymentStatus, String normalizedFilter) {
        if ("all".equals(normalizedFilter)) {
            return true;
        }
        return normalizedFilter.equalsIgnoreCase(paymentStatus);
    }

    private String normalizeSummaryFilter(String statusFilter) {
        if (!StringUtils.hasText(statusFilter) || "ALL".equalsIgnoreCase(statusFilter)) {
            return "all";
        }
        String normalized = statusFilter.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BELUM_LUNAS" -> "unpaid";
            case "LUNAS" -> "paid";
            case "JATUH_TEMPO" -> "overdue";
            default -> throw new IllegalArgumentException("Status pembayaran tidak valid. Gunakan ALL, BELUM_LUNAS, LUNAS, atau JATUH_TEMPO.");
        };
    }

    private String normalizeCustomerStatus(String status) {
        String normalized = trimToNull(status);
        if (!StringUtils.hasText(normalized)) {
            return "pending";
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if ("active".equals(normalized)) {
            return "active";
        }
        if ("suspended".equals(normalized)) {
            return "suspended";
        }
        if ("inactive".equals(normalized) || "nonaktif".equals(normalized)) {
            return "inactive";
        }
        return "pending";
    }

    private String normalizePaymentMethod(String paymentMethod) {
        String normalized = trimToNull(paymentMethod);
        if (normalized == null) {
            return "Cash";
        }
        return "TRANSFER".equalsIgnoreCase(normalized) ? "Transfer" : "Cash";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
