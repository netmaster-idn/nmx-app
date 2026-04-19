package com.netmaster.nmx.controller;

import com.netmaster.nmx.model.Customer;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.model.Invoice;
import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import com.netmaster.nmx.repository.InvoiceRepository;
import com.netmaster.nmx.repository.PaymentHistoryRepository;
import com.netmaster.nmx.service.WhatsappGatewayService;
import com.netmaster.nmx.service.BillingQuickActionService;
import com.netmaster.nmx.service.InvoiceNumberService;
import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.WhatsappStatusData;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BillingQuickActionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerServiceEntityRepository customerServiceEntityRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PaymentHistoryRepository paymentHistoryRepository;

    @Autowired
    private BillingQuickActionService billingQuickActionService;

    @Autowired
    private InvoiceNumberService invoiceNumberService;

    @MockBean
    private WhatsappGatewayService whatsappGatewayService;

    @Test
    void payInvoiceBeforeDueDate_generatesNextInvoiceFromDueDate() throws Exception {
        mockWhatsappReady();
        Customer customer = createCustomer("active");
        CustomerServiceEntity service = createService(customer, "active", new BigDecimal("175000"));
        Invoice invoice = createInvoice(customer, service, LocalDate.of(2026, 3, 20), "pending");

        mockMvc.perform(post("/api/invoices/{id}/pay", invoice.getId())
                        .with(adminUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"paymentDate\": \"2026-03-18\",
                                  \"paymentMethod\": \"CASH\"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("paid"));

        Invoice paidInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(paidInvoice.getStatus()).isEqualToIgnoringCase("paid");
        assertThat(paymentHistoryRepository.findByInvoiceIdOrderByPaymentDateAscIdAsc(invoice.getId()))
                .anySatisfy(history -> assertThat(history.getDescription()).isEqualTo("BULANAN"));

        List<Invoice> customerInvoices = invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customer.getId());
        Invoice nextInvoice = customerInvoices.stream()
                .filter(item -> !item.getId().equals(invoice.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(nextInvoice.getDueDate()).isEqualTo(LocalDate.of(2026, 4, 20));
        assertThat(nextInvoice.getStatus()).isEqualToIgnoringCase("pending");
        verify(whatsappGatewayService).sendDocument(any());
    }

    @Test
    void payInvoiceAfterDueDate_reactivatesSuspendedCustomerAndShiftsNextDueDateToPaymentDate() throws Exception {
        mockWhatsappReady();
        Customer customer = createCustomer("suspended");
        CustomerServiceEntity service = createService(customer, "suspended", new BigDecimal("200000"));
        Invoice invoice = createInvoice(customer, service, LocalDate.of(2026, 3, 5), "pending");

        mockMvc.perform(post("/api/invoices/{id}/pay", invoice.getId())
                        .with(adminUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"paymentDate\": \"2026-03-18\",
                                  \"paymentMethod\": \"TRANSFER\"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentMethod").value("Transfer"));

        Customer updatedCustomer = customerRepository.findById(customer.getId()).orElseThrow();
        CustomerServiceEntity updatedService = customerServiceEntityRepository.findById(service.getId()).orElseThrow();
        assertThat(updatedCustomer.getStatus()).isEqualTo("active");
        assertThat(updatedService.getStatus()).isEqualTo("active");

        Invoice nextInvoice = invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customer.getId()).stream()
                .filter(item -> !item.getId().equals(invoice.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(nextInvoice.getDueDate()).isEqualTo(LocalDate.of(2026, 4, 18));
    }

    @Test
    void recordPaymentEndpoint_afterDueDate_generatesNextInvoiceUsingPaymentDateAnchor() throws Exception {
        mockWhatsappReady();
        Customer customer = createCustomer("active");
        CustomerServiceEntity service = createService(customer, "active", new BigDecimal("180000"));
        Invoice invoice = createInvoice(customer, service, LocalDate.of(2026, 3, 10), "overdue");

        mockMvc.perform(post("/api/invoices/{id}/payments", invoice.getId())
                        .with(adminUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"amount\": 180000,
                                  \"paymentDate\": \"2026-03-18\",
                                  \"paymentMethod\": \"CASH\"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("paid"));

        Invoice nextInvoice = invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customer.getId()).stream()
                .filter(item -> !item.getId().equals(invoice.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(nextInvoice.getDueDate()).isEqualTo(LocalDate.of(2026, 4, 18));
        assertThat(nextInvoice.getBillingMonth()).isEqualTo(LocalDate.of(2026, 4, 18));
    }

    @Test
    void payInvoice_whenWhatsappSendFails_paymentStillSucceeds() throws Exception {
        mockWhatsappReady();
        doThrow(new IllegalStateException("Gateway WhatsApp tidak dapat dihubungi"))
                .when(whatsappGatewayService).sendDocument(any());

        Customer customer = createCustomer("active");
        CustomerServiceEntity service = createService(customer, "active", new BigDecimal("175000"));
        Invoice invoice = createInvoice(customer, service, LocalDate.of(2026, 3, 20), "pending");

        mockMvc.perform(post("/api/invoices/{id}/pay", invoice.getId())
                        .with(adminUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"paymentDate\": \"2026-03-18\",
                                  \"paymentMethod\": \"CASH\"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("paid"));

        Invoice paidInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(paidInvoice.getStatus()).isEqualToIgnoringCase("paid");
    }

    @Test
    void autoSuspendOverdueCustomers_marksCustomerAndServiceSuspended() {
        Customer customer = createCustomer("active");
        CustomerServiceEntity service = createService(customer, "active", new BigDecimal("150000"));
        createInvoice(customer, service, LocalDate.now().minusDays(1), "pending");

        billingQuickActionService.autoSuspendOverdueCustomers();

        Customer updatedCustomer = customerRepository.findById(customer.getId()).orElseThrow();
        CustomerServiceEntity updatedService = customerServiceEntityRepository.findById(service.getId()).orElseThrow();
        assertThat(updatedCustomer.getStatus()).isEqualTo("suspended");
        assertThat(updatedService.getStatus()).isEqualTo("suspended");
    }

    @Test
    void generateMonthlyInvoices_keepsGeneratingNextInvoiceAndStoresNoPaymentHistory() {
        Customer customer = createCustomer("suspended");
        CustomerServiceEntity service = createService(customer, "suspended", new BigDecimal("150000"));
        Invoice marchInvoice = createInvoice(customer, service, LocalDate.of(2026, 3, 20), "overdue");

        billingQuickActionService.generateMonthlyInvoices();

        List<Invoice> customerInvoices = invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customer.getId());
        assertThat(customerInvoices)
                .extracting(Invoice::getDueDate)
                .contains(LocalDate.of(2026, 4, 20));

        assertThat(paymentHistoryRepository.findByInvoiceIdOrderByPaymentDateAscIdAsc(marchInvoice.getId()))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.getDescription()).isEqualTo("TIDAK_BAYAR");
                    assertThat(history.getMethod()).isEqualTo("SYSTEM");
                    assertThat(history.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
                    assertThat(history.getPaymentDate()).isEqualTo(LocalDate.of(2026, 3, 31));
                });
        assertThat(invoiceRepository.findById(marchInvoice.getId()).orElseThrow().getStatus())
                .isEqualTo("no_payment");
    }

    @Test
    void financeGenerateEndpoint_executesMonthlyBillingAndReturnsCreatedCount() throws Exception {
        Customer customer = createCustomer("active");
        CustomerServiceEntity service = createService(customer, "active", new BigDecimal("210000"));
        createInvoice(customer, service, LocalDate.now().minusMonths(1), "overdue");

        mockMvc.perform(post("/api/finance/billing/generate")
                        .with(adminUser())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.generated").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void payCurrentMonthInvoice_afterPreviousMonthNoPayment_usesPaymentDateAsNextDueDateAndUpdatesActivationDate() throws Exception {
        mockWhatsappReady();
        Customer customer = createCustomer("suspended");
        CustomerServiceEntity service = createService(customer, "suspended", new BigDecimal("200000"));
        Invoice marchInvoice = createInvoice(customer, service, LocalDate.of(2026, 3, 5), "overdue");
        Invoice aprilInvoice = createInvoice(customer, service, LocalDate.of(2026, 4, 5), "overdue");

        billingQuickActionService.generateMonthlyInvoices();

        mockMvc.perform(post("/api/invoices/{id}/pay", aprilInvoice.getId())
                        .with(adminUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"paymentDate\": \"2026-04-18\",
                                  \"paymentMethod\": \"TRANSFER\"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        CustomerServiceEntity updatedService = customerServiceEntityRepository.findById(service.getId()).orElseThrow();
        assertThat(updatedService.getActivationDate()).isEqualTo(LocalDate.of(2026, 4, 18));
        assertThat(invoiceRepository.findById(marchInvoice.getId()).orElseThrow().getStatus()).isEqualTo("no_payment");

        Invoice nextInvoice = invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customer.getId()).stream()
                .filter(item -> !item.getId().equals(aprilInvoice.getId()))
                .filter(item -> !item.getId().equals(marchInvoice.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(nextInvoice.getDueDate()).isEqualTo(LocalDate.of(2026, 5, 18));
    }

    @Test
    void deleteCustomerWithWrongPassword_returnsForbidden() throws Exception {
        Customer customer = createCustomer("active");
        createService(customer, "active", new BigDecimal("150000"));

        mockMvc.perform(delete("/api/customers/{id}", customer.getId())
                        .with(adminUser())
                        .with(csrf())
                        .header("X-DELETE-CONFIRMED", "true")
                        .header("X-SUPERADMIN-PASSWORD", "wrong-password"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    private Customer createCustomer(String status) {
        Customer customer = new Customer();
        customer.setCustomerCode("CUST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        customer.setFullName("Customer " + UUID.randomUUID());
        customer.setPhone("08" + System.currentTimeMillis());
        customer.setInstallationAddress("Jl. Test Billing");
        customer.setStatus(status);
        customer.setRegistrationDate(LocalDate.of(2026, 2, 1));
        return customerRepository.save(customer);
    }

    private CustomerServiceEntity createService(Customer customer, String status, BigDecimal monthlyFee) {
        CustomerServiceEntity service = new CustomerServiceEntity();
        service.setCustomer(customer);
        service.setMonthlyFee(monthlyFee);
        service.setInstallationFee(BigDecimal.ZERO);
        service.setActivationDate(LocalDate.of(2026, 2, 1));
        service.setStatus(status);
        return customerServiceEntityRepository.save(service);
    }

    private Invoice createInvoice(Customer customer, CustomerServiceEntity service, LocalDate dueDate, String status) {
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invoiceNumberService.generate());
        invoice.setCustomer(customer);
        invoice.setCustomerService(service);
        invoice.setBillingMonth(dueDate);
        invoice.setDueDate(dueDate);
        invoice.setMonthlyFee(service.getMonthlyFee());
        invoice.setInstallationFee(BigDecimal.ZERO);
        invoice.setOtherCharges(BigDecimal.ZERO);
        invoice.setTotalAmount(service.getMonthlyFee());
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setInvoiceType("subscription");
        invoice.setStatus(status);
        return invoiceRepository.save(invoice);
    }

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor adminUser() {
        return user("integration-admin").authorities(() -> "ROLE_SUPER_ADMIN");
    }

    private void mockWhatsappReady() {
        when(whatsappGatewayService.getStatus()).thenReturn(ApiResponse.success(
                "ready",
                new WhatsappStatusData(
                        "connected",
                        "Connected",
                        true,
                        true,
                        null,
                        "default",
                        null,
                        null,
                        null,
                        null
                )
        ));
        when(whatsappGatewayService.isReady(any())).thenReturn(true);
        when(whatsappGatewayService.sendDocument(any())).thenReturn(ApiResponse.success(
                "sent",
                java.util.Map.of("messageId", "msg-test")
        ));
    }
}
