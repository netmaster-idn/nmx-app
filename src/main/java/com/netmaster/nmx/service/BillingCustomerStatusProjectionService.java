package com.netmaster.nmx.service;

import com.netmaster.nmx.model.Customer;
import com.netmaster.nmx.model.CustomerBillingStatus;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.model.Invoice;
import com.netmaster.nmx.model.InvoiceDeliveryLog;
import com.netmaster.nmx.model.Payment;
import com.netmaster.nmx.model.PppoeActionLog;
import com.netmaster.nmx.repository.CustomerBillingStatusRepository;
import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import com.netmaster.nmx.repository.InvoiceDeliveryLogRepository;
import com.netmaster.nmx.repository.InvoiceRepository;
import com.netmaster.nmx.repository.PaymentRepository;
import com.netmaster.nmx.repository.PppoeActionLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingCustomerStatusProjectionService {

    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceDeliveryLogRepository deliveryLogRepository;
    private final PppoeActionLogRepository pppoeActionLogRepository;
    private final CustomerBillingStatusRepository projectionRepository;
    private final CustomerServiceEntityRepository serviceRepository;
    private final BillingStatusSupport billingStatusSupport;
    private final BillingAutomationSettingsService settingsService;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void refreshAll() {
        refreshCustomers(customerRepository.findAll().stream().map(Customer::getId).filter(Objects::nonNull).toList());
    }

    @Transactional
    public void refreshCustomers(Collection<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return;
        }
        int disableThreshold = settingsService.getOrCreate().getLatePaymentDisableDays();
        int sendDaysBeforeDue = settingsService.getOrCreate().getInvoiceSendDaysBeforeDue();
        for (Long customerId : customerIds) {
            Customer customer = customerRepository.findById(customerId).orElse(null);
            if (customer == null) {
                continue;
            }
            CustomerBillingStatus status = projectionRepository.findByCustomerId(customerId)
                    .orElseGet(() -> {
                        CustomerBillingStatus projection = new CustomerBillingStatus();
                        projection.setCustomer(customer);
                        return projection;
                    });

            List<Invoice> invoices = invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customerId);
            Invoice currentInvoice = selectCurrentInvoice(invoices);
            Payment latestPayment = currentInvoice != null
                    ? paymentRepository.findByInvoiceIdOrderByPaymentDateDescIdDesc(currentInvoice.getId()).stream().findFirst().orElse(null)
                    : paymentRepository.findByCustomerIdOrderByPaymentDateDescIdDesc(customerId).stream().findFirst().orElse(null);
            InvoiceDeliveryLog lastInvoiceLog = currentInvoice != null
                    ? safeFindDeliveryLog(currentInvoice.getId(), "invoice").orElse(null)
                    : null;
            InvoiceDeliveryLog lastReceiptLog = currentInvoice != null
                    ? safeFindDeliveryLog(currentInvoice.getId(), "receipt").orElse(null)
                    : null;
            PppoeActionLog lastPppoeAction = safeFindLastPppoeAction(customerId).orElse(null);
            CustomerServiceEntity latestService = serviceRepository.findTopByCustomerIdOrderByCreatedAtDesc(customerId).orElse(null);

            String invoiceStatus = currentInvoice != null
                    ? billingStatusSupport.normalizeInvoiceStatus(
                    currentInvoice.getWhatsappStatus() != null ? currentInvoice.getWhatsappStatus() : currentInvoice.getStatus(),
                    currentInvoice.getDueDate(),
                    currentInvoice.getTotalAmount(),
                    currentInvoice.getAmountPaid()
            )
                    : "draft";
            String paymentStatus = billingStatusSupport.normalizePaymentStatus(latestPayment, currentInvoice);
            int overdueDays = currentInvoice != null && currentInvoice.getDueDate() != null
                    && currentInvoice.getDueDate().isBefore(LocalDate.now())
                    && !"paid".equals(invoiceStatus)
                    ? (int) ChronoUnit.DAYS.between(currentInvoice.getDueDate(), LocalDate.now())
                    : 0;

            status.setCurrentInvoiceStatus(invoiceStatus);
            status.setCurrentPaymentStatus(paymentStatus);
            status.setLastInvoiceSentAt(lastInvoiceLog != null ? lastInvoiceLog.getSentAt() : currentInvoice != null ? currentInvoice.getSentAt() : null);
            status.setLastReceiptSentAt(lastReceiptLog != null ? lastReceiptLog.getSentAt() : latestPayment != null ? latestPayment.getReceiptSentAt() : null);
            status.setOverdueDays(overdueDays);
            status.setNextInvoiceSendDate(currentInvoice != null && currentInvoice.getDueDate() != null
                    ? currentInvoice.getDueDate().minusDays(sendDaysBeforeDue)
                    : null);
            status.setEligibleForDisable(overdueDays >= disableThreshold
                    && latestService != null
                    && latestService.getPppoeUsername() != null
                    && !"disabled".equals(billingStatusSupport.normalizePppoeStatus(customer.getPppoeStatus())));
            status.setPppoeCurrentState(lastPppoeAction != null && "success".equalsIgnoreCase(lastPppoeAction.getStatus())
                    ? ("disable".equalsIgnoreCase(lastPppoeAction.getActionType()) ? "disabled" : "active")
                    : billingStatusSupport.normalizePppoeStatus(customer.getPppoeStatus()));
            status.setUpdatedAt(LocalDateTime.now());
            projectionRepository.save(status);
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
                log.warn("Invoice delivery log table is not available for the current tenant yet. Skipping projection log lookup.");
                return Optional.empty();
            }
            throw ex;
        }
    }

    private Optional<PppoeActionLog> safeFindLastPppoeAction(Long customerId) {
        if (!tableExists("pppoe_action_logs")) {
            return Optional.empty();
        }
        try {
            return pppoeActionLogRepository.findByCustomerIdInOrderByCreatedAtDesc(List.of(customerId)).stream().findFirst();
        } catch (DataAccessException ex) {
            if (isMissingRelation(ex, "pppoe_action_logs")) {
                log.warn("PPPoE action log table is not available for the current tenant yet. Skipping projection action lookup.");
                return Optional.empty();
            }
            throw ex;
        }
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
        if (invoice == null) {
            return false;
        }
        String status = billingStatusSupport.normalizeInvoiceStatus(
                invoice.getStatus(),
                invoice.getDueDate(),
                invoice.getTotalAmount(),
                invoice.getAmountPaid()
        );
        return !"paid".equals(status);
    }

    private boolean isCollectibleInvoice(Invoice invoice) {
        if (invoice == null) {
            return false;
        }
        String status = billingStatusSupport.normalizeInvoiceStatus(
                invoice.getStatus(),
                invoice.getDueDate(),
                invoice.getTotalAmount(),
                invoice.getAmountPaid()
        );
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
}
