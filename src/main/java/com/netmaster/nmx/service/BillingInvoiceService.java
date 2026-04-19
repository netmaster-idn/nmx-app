package com.netmaster.nmx.service;

import com.netmaster.nmx.dto.InvoiceDTO;
import com.netmaster.nmx.dto.InvoiceRowDTO;
import com.netmaster.nmx.model.BankAccount;
import com.netmaster.nmx.model.CompanyProfile;
import com.netmaster.nmx.model.Customer;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.model.Invoice;
import com.netmaster.nmx.model.Payment;
import com.netmaster.nmx.repository.BankAccountRepository;
import com.netmaster.nmx.repository.CompanyProfileRepository;
import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import com.netmaster.nmx.repository.InvoiceRepository;
import com.netmaster.nmx.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BillingInvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    private final CustomerServiceEntityRepository customerServiceEntityRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final BankAccountRepository bankAccountRepository;
    private final InvoiceNumberService invoiceNumberService;

    @Transactional
    public Invoice createInvoice(InvoiceDTO dto) {
        Customer customer = getCustomerById(dto.getCustomerId());
        CustomerServiceEntity customerService = resolveCustomerServiceForInvoice(customer, dto.getCustomerServiceId());

        LocalDate billingMonth = dto.getDueDate() != null ? dto.getDueDate() : dto.getBillingMonth();
        if (billingMonth == null) {
            billingMonth = resolveNextBillingAnchor(customer, customerService, null);
        }

        validateInvoiceUniqueness(dto.getCustomerId(), billingMonth, null);

        LocalDate dueDate = dto.getDueDate() != null
                ? dto.getDueDate()
                : resolveNextDueDate(customer, customerService, null, billingMonth);

        BigDecimal monthlyFee = dto.getMonthlyFee() != null
                ? dto.getMonthlyFee()
                : customerService != null ? defaultAmount(customerService.getMonthlyFee()) : BigDecimal.ZERO;
        BigDecimal installationFee = dto.getInstallationFee() != null ? dto.getInstallationFee() : BigDecimal.ZERO;
        BigDecimal otherCharges = dto.getOtherCharges() != null ? dto.getOtherCharges() : BigDecimal.ZERO;
        BigDecimal totalAmount = monthlyFee.add(installationFee).add(otherCharges);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invoiceNumberService.generate());
        invoice.setCustomer(customer);
        invoice.setCustomerService(customerService);
        applyCompanyContext(invoice, customerService);
        invoice.setBillingMonth(billingMonth);
        invoice.setDueDate(dueDate);
        invoice.setMonthlyFee(monthlyFee);
        invoice.setInstallationFee(installationFee);
        invoice.setOtherCharges(otherCharges);
        invoice.setTotalAmount(totalAmount);
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setStatus(computeInvoiceStatus(invoice, BigDecimal.ZERO));
        invoice.setInvoiceType(normalizeInvoiceType("subscription"));
        invoice.setNotes(dto.getNotes());
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public Invoice updateInvoice(Long id, InvoiceDTO dto) {
        Invoice existing = getInvoiceEntityById(id);
        Customer customer = getCustomerById(dto.getCustomerId());
        CustomerServiceEntity customerService = resolveCustomerServiceForInvoice(customer, dto.getCustomerServiceId());

        LocalDate billingMonth = dto.getDueDate() != null ? dto.getDueDate() : dto.getBillingMonth();
        if (billingMonth == null) {
            billingMonth = existing.getBillingMonth() != null
                    ? existing.getBillingMonth()
                    : resolveNextBillingAnchor(customer, customerService, id);
        }

        validateInvoiceUniqueness(dto.getCustomerId(), billingMonth, id);

        LocalDate dueDate = dto.getDueDate() != null
                ? dto.getDueDate()
                : resolveNextDueDate(customer, customerService, id, billingMonth);
        BigDecimal monthlyFee = dto.getMonthlyFee() != null
                ? dto.getMonthlyFee()
                : existing.getMonthlyFee() != null ? existing.getMonthlyFee()
                : customerService != null ? defaultAmount(customerService.getMonthlyFee()) : BigDecimal.ZERO;
        BigDecimal installationFee = dto.getInstallationFee() != null
                ? dto.getInstallationFee()
                : defaultAmount(existing.getInstallationFee());
        BigDecimal otherCharges = dto.getOtherCharges() != null
                ? dto.getOtherCharges()
                : defaultAmount(existing.getOtherCharges());

        existing.setCustomer(customer);
        existing.setCustomerService(customerService);
        applyCompanyContext(existing, customerService);
        existing.setBillingMonth(billingMonth);
        existing.setDueDate(dueDate);
        existing.setMonthlyFee(monthlyFee);
        existing.setInstallationFee(installationFee);
        existing.setOtherCharges(otherCharges);
        existing.setTotalAmount(monthlyFee.add(installationFee).add(otherCharges));
        existing.setInvoiceType(normalizeInvoiceType(existing.getInvoiceType()));
        existing.setNotes(dto.getNotes());

        BigDecimal amountPaid = defaultAmount(paymentRepository.sumAmountPaidByInvoiceId(existing.getId()));
        if (!"cancelled".equalsIgnoreCase(existing.getStatus())) {
            existing.setStatus(computeInvoiceStatus(existing, amountPaid));
        }

        return invoiceRepository.save(existing);
    }

    @Transactional
    public InvoiceRowDTO getInvoiceRowById(Long id) {
        Invoice invoice = getInvoiceEntityById(id);
        return buildInvoiceRows(List.of(invoice)).stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Invoice tidak ditemukan!"));
    }

    @Transactional
    public List<InvoiceRowDTO> getAllInvoiceRows() {
        return buildInvoiceRows(invoiceRepository.findAll());
    }

    @Transactional
    public List<InvoiceRowDTO> getInvoiceRowsByCustomer(Long customerId) {
        return buildInvoiceRows(invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customerId));
    }

    @Transactional
    public List<InvoiceRowDTO> getInvoiceRowsByCustomerIds(Collection<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        return buildInvoiceRows(invoiceRepository.findByCustomerIdIn(customerIds));
    }

    @Transactional
    public Invoice cancelInvoice(Long invoiceId) {
        Invoice invoice = getInvoiceEntityById(invoiceId);
        if ("paid".equalsIgnoreCase(invoice.getStatus())) {
            throw new RuntimeException("Invoice sudah lunas tidak dapat dibatalkan!");
        }
        invoice.setStatus("cancelled");
        return invoiceRepository.save(invoice);
    }

    @Transactional
    public void deleteInvoice(Long invoiceId) {
        Invoice invoice = getInvoiceEntityById(invoiceId);
        if (paymentRepository.countByInvoiceId(invoiceId) > 0) {
            throw new RuntimeException("Invoice yang sudah memiliki pembayaran tidak dapat dihapus!");
        }
        invoiceRepository.delete(invoice);
    }

    @Transactional
    public Invoice refreshInvoiceSnapshot(Long invoiceId) {
        return refreshInvoiceSnapshot(getInvoiceEntityById(invoiceId), null);
    }

    @Transactional
    public Invoice refreshInvoiceSnapshot(Invoice invoice) {
        return refreshInvoiceSnapshot(invoice, null);
    }

    @Transactional
    public List<Invoice> refreshInvoiceSnapshots(List<Invoice> invoices) {
        if (invoices == null || invoices.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Payment>> paymentsByInvoice = loadPaymentsByInvoice(invoices);
        return invoices.stream()
                .map(invoice -> refreshInvoiceSnapshot(invoice, paymentsByInvoice.get(invoice.getId())))
                .toList();
    }

    public List<Integer> getDistinctInvoiceYears() {
        return invoiceRepository.findAll().stream()
                .map(this::resolveInvoiceReferenceDate)
                .filter(Objects::nonNull)
                .map(LocalDate::getYear)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    public List<Integer> getDistinctInvoiceMonthsByYear(Integer year) {
        if (year == null) {
            return List.of();
        }
        return invoiceRepository.findAll().stream()
                .map(this::resolveInvoiceReferenceDate)
                .filter(Objects::nonNull)
                .filter(referenceDate -> referenceDate.getYear() == year)
                .map(LocalDate::getMonthValue)
                .distinct()
                .sorted()
                .toList();
    }

    @Transactional(readOnly = true)
    public Invoice getInvoiceEntityById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice tidak ditemukan!"));
    }

    public void applyCompanyContext(Invoice invoice, CustomerServiceEntity customerService) {
        if (invoice == null) {
            return;
        }

        CompanyProfile companyProfile = resolveInvoiceCompanyProfile(invoice, customerService);
        invoice.setCompanyProfile(companyProfile);

        if (companyProfile == null) {
            invoice.setBankAccount(null);
            return;
        }

        BankAccount bankAccount = resolveInvoiceBankAccount(invoice, companyProfile);
        invoice.setBankAccount(bankAccount);
    }

    private List<InvoiceRowDTO> buildInvoiceRows(List<Invoice> invoices) {
        if (invoices == null || invoices.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Payment>> paymentsByInvoice = loadPaymentsByInvoice(invoices);
        List<Invoice> refreshedInvoices = invoices.stream()
                .map(invoice -> refreshInvoiceSnapshot(invoice, paymentsByInvoice.get(invoice.getId())))
                .toList();

        return refreshedInvoices.stream()
                .map(invoice -> toInvoiceRow(invoice, paymentsByInvoice.get(invoice.getId())))
                .toList();
    }

    private Map<Long, List<Payment>> loadPaymentsByInvoice(List<Invoice> invoices) {
        List<Long> invoiceIds = invoices.stream()
                .map(Invoice::getId)
                .filter(Objects::nonNull)
                .toList();
        if (invoiceIds.isEmpty()) {
            return Map.of();
        }

        return paymentRepository.findByInvoiceIds(invoiceIds).stream()
                .filter(payment -> payment.getInvoice() != null && payment.getInvoice().getId() != null)
                .collect(Collectors.groupingBy(payment -> payment.getInvoice().getId(), HashMap::new, Collectors.toList()));
    }

    private Invoice refreshInvoiceSnapshot(Invoice invoice, List<Payment> existingPayments) {
        if (invoice == null) {
            return null;
        }

        List<Payment> payments = existingPayments != null
                ? existingPayments
                : paymentRepository.findByInvoiceIdOrderByPaymentDateDescIdDesc(invoice.getId());

        BigDecimal amountPaid = payments.stream()
                .map(Payment::getAmountPaid)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Payment latestPayment = payments.stream()
                .max(Comparator.comparing(Payment::getPaymentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Payment::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        String computedStatus = computeInvoiceStatus(invoice, amountPaid);
        boolean changed = false;

        if (invoice.getAmountPaid() == null || invoice.getAmountPaid().compareTo(amountPaid) != 0) {
            invoice.setAmountPaid(amountPaid);
            changed = true;
        }

        LocalDate latestPaymentDate = latestPayment != null ? latestPayment.getPaymentDate() : null;
        if (!Objects.equals(invoice.getPaymentDate(), latestPaymentDate)) {
            invoice.setPaymentDate(latestPaymentDate);
            changed = true;
        }

        String latestPaymentMethod = latestPayment != null ? latestPayment.getPaymentMethod() : null;
        if (!Objects.equals(invoice.getPaymentMethod(), latestPaymentMethod)) {
            invoice.setPaymentMethod(latestPaymentMethod);
            changed = true;
        }

        String latestPaymentNotes = latestPayment != null ? latestPayment.getNotes() : null;
        if (!Objects.equals(invoice.getPaymentNotes(), latestPaymentNotes)) {
            invoice.setPaymentNotes(latestPaymentNotes);
            changed = true;
        }

        if (!Objects.equals(invoice.getStatus(), computedStatus)) {
            invoice.setStatus(computedStatus);
            changed = true;
        }

        if (changed) {
            return invoiceRepository.save(invoice);
        }
        return invoice;
    }

    private InvoiceRowDTO toInvoiceRow(Invoice invoice, List<Payment> payments) {
        BigDecimal totalAmount = defaultAmount(invoice.getTotalAmount());
        BigDecimal amountPaid = payments == null
                ? defaultAmount(invoice.getAmountPaid())
                : payments.stream()
                        .map(Payment::getAmountPaid)
                        .filter(Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstandingAmount = totalAmount.subtract(amountPaid).max(BigDecimal.ZERO);

        Payment latestPayment = payments == null ? null : payments.stream()
                .max(Comparator.comparing(Payment::getPaymentDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Payment::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);

        Customer customer = invoice.getCustomer();
        CustomerServiceEntity service = invoice.getCustomerService();

        return new InvoiceRowDTO(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                customer != null ? customer.getId() : null,
                customer != null ? customer.getCustomerCode() : null,
                customer != null ? customer.getFullName() : null,
                service != null ? service.getId() : null,
                invoice.getBillingMonth(),
                invoice.getDueDate(),
                defaultAmount(invoice.getMonthlyFee()),
                defaultAmount(invoice.getInstallationFee()),
                defaultAmount(invoice.getOtherCharges()),
                totalAmount,
                amountPaid,
                outstandingAmount,
                computeInvoiceStatus(invoice, amountPaid),
                latestPayment != null ? latestPayment.getPaymentDate() : null,
                latestPayment != null ? latestPayment.getPaymentMethod() : null,
                normalizeInvoiceType(invoice.getInvoiceType()),
                resolveInvoiceTypeLabel(invoice),
                latestPayment != null ? latestPayment.getNotes() : null,
                invoice.getNotes()
        );
    }

    private Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pelanggan tidak ditemukan!"));
    }

    private LocalDate resolveInvoiceReferenceDate(Invoice invoice) {
        if (invoice == null) {
            return null;
        }
        if (invoice.getBillingMonth() != null) {
            return invoice.getBillingMonth();
        }
        if (invoice.getDueDate() != null) {
            return invoice.getDueDate();
        }
        return invoice.getPaymentDate();
    }

    private void validateInvoiceUniqueness(Long customerId, LocalDate billingMonth, Long invoiceId) {
        if (customerId == null || billingMonth == null) {
            return;
        }

        Customer customer = getCustomerById(customerId);
        invoiceRepository.findByCustomerCodeAndBillingMonth(customer.getCustomerCode(), billingMonth)
                .filter(invoice -> !invoice.getId().equals(invoiceId))
                .ifPresent(invoice -> {
                    throw new RuntimeException("Invoice untuk pelanggan dan periode tagihan tersebut sudah ada!");
                });
    }

    private CustomerServiceEntity resolveCustomerServiceForInvoice(Customer customer, Long customerServiceId) {
        if (customerServiceId != null) {
            return customerServiceEntityRepository.findById(customerServiceId)
                    .orElseThrow(() -> new RuntimeException("Layanan tidak ditemukan!"));
        }

        if (customer == null || customer.getId() == null) {
            return null;
        }

        return customerServiceEntityRepository.findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .orElse(null);
    }

    private CompanyProfile resolveInvoiceCompanyProfile(Invoice invoice, CustomerServiceEntity customerService) {
        if (invoice != null && invoice.getCompanyProfile() != null) {
            return invoice.getCompanyProfile();
        }
        if (customerService != null && customerService.getOdp() != null && customerService.getOdp().getCompanyProfile() != null) {
            return customerService.getOdp().getCompanyProfile();
        }
        return companyProfileRepository.findFirstByOrderByIdAsc()
                .or(() -> companyProfileRepository.findByIsActiveTrue())
                .orElse(null);
    }

    private BankAccount resolveInvoiceBankAccount(Invoice invoice, CompanyProfile companyProfile) {
        if (invoice != null && invoice.getBankAccount() != null) {
            Long bankCompanyId = invoice.getBankAccount().getCompanyProfile() != null
                    ? invoice.getBankAccount().getCompanyProfile().getId()
                    : null;
            if (companyProfile != null && Objects.equals(bankCompanyId, companyProfile.getId())) {
                return invoice.getBankAccount();
            }
        }
        if (companyProfile == null || companyProfile.getId() == null) {
            return null;
        }
        return bankAccountRepository.findFirstByCompanyProfileIdAndIsPrimaryTrueAndIsActiveTrueOrderByIdAsc(companyProfile.getId())
                .or(() -> bankAccountRepository.findFirstByCompanyProfileIdAndIsActiveTrueOrderByIsPrimaryDescIdAsc(companyProfile.getId()))
                .orElse(null);
    }

    private LocalDate resolveNextBillingAnchor(Customer customer, CustomerServiceEntity service, Long currentInvoiceId) {
        LocalDate referenceDate = resolveRecurringReferenceDate(customer, service, currentInvoiceId);
        return referenceDate.plusMonths(1);
    }

    private LocalDate resolveNextDueDate(Customer customer, CustomerServiceEntity service, Long currentInvoiceId, LocalDate billingMonth) {
        if (billingMonth != null) {
            return billingMonth;
        }
        return resolveNextBillingAnchor(customer, service, currentInvoiceId);
    }

    private LocalDate resolveRecurringReferenceDate(Customer customer, CustomerServiceEntity service, Long currentInvoiceId) {
        if (customer != null && customer.getId() != null) {
            LocalDate latestDueDate = findLatestScheduledDate(customer.getId(), currentInvoiceId);
            if (latestDueDate != null) {
                return latestDueDate;
            }
        }
        return resolveBillingStartDate(customer, service);
    }

    private LocalDate resolveBillingStartDate(Customer customer, CustomerServiceEntity service) {
        if (customer != null && customer.getRegistrationDate() != null) {
            return customer.getRegistrationDate();
        }
        if (service != null && service.getInstallationDate() != null) {
            return service.getInstallationDate();
        }
        if (service != null && service.getActivationDate() != null) {
            return service.getActivationDate();
        }
        return LocalDate.now();
    }

    private LocalDate findLatestScheduledDate(Long customerId, Long currentInvoiceId) {
        return invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customerId).stream()
                .filter(invoice -> currentInvoiceId == null || !invoice.getId().equals(currentInvoiceId))
                .filter(invoice -> !"cancelled".equalsIgnoreCase(invoice.getStatus()))
                .map(invoice -> invoice.getDueDate() != null ? invoice.getDueDate() : invoice.getBillingMonth())
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private String computeInvoiceStatus(Invoice invoice, BigDecimal amountPaid) {
        if (invoice == null) {
            return "pending";
        }
        if ("cancelled".equalsIgnoreCase(invoice.getStatus())) {
            return "cancelled";
        }
        if ("no_payment".equalsIgnoreCase(invoice.getStatus())
                || "no-payment".equalsIgnoreCase(invoice.getStatus())
                || "tidak_bayar".equalsIgnoreCase(invoice.getStatus())) {
            return "no_payment";
        }

        BigDecimal totalAmount = defaultAmount(invoice.getTotalAmount());
        BigDecimal normalizedAmountPaid = defaultAmount(amountPaid);
        if (totalAmount.signum() == 0 || normalizedAmountPaid.compareTo(totalAmount) >= 0) {
            return "paid";
        }
        if (invoice.getDueDate() != null && invoice.getDueDate().isBefore(LocalDate.now())) {
            return "overdue";
        }
        if (normalizedAmountPaid.signum() > 0) {
            return "partial";
        }
        return "pending";
    }

    private String normalizeInvoiceType(String invoiceType) {
        if (!StringUtils.hasText(invoiceType)) {
            return "subscription";
        }
        return switch (invoiceType.trim().toLowerCase()) {
            case "activation" -> "activation";
            default -> "subscription";
        };
    }

    private String resolveInvoiceTypeLabel(Invoice invoice) {
        return switch (normalizeInvoiceType(invoice != null ? invoice.getInvoiceType() : null)) {
            case "activation" -> "Pembayaran Aktivasi Pelanggan";
            default -> "Pembayaran Langganan Bulanan";
        };
    }
}
