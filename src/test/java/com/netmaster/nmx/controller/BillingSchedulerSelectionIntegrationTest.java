package com.netmaster.nmx.controller;

import com.netmaster.nmx.model.Customer;
import com.netmaster.nmx.model.CustomerBillingStatus;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.model.Invoice;
import com.netmaster.nmx.repository.CustomerBillingStatusRepository;
import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import com.netmaster.nmx.repository.InvoiceRepository;
import com.netmaster.nmx.service.BillingAutomationSettingsService;
import com.netmaster.nmx.service.BillingCustomerStatusProjectionService;
import com.netmaster.nmx.service.InvoiceNumberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BillingSchedulerSelectionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerServiceEntityRepository customerServiceEntityRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private CustomerBillingStatusRepository customerBillingStatusRepository;

    @Autowired
    private BillingCustomerStatusProjectionService projectionService;

    @Autowired
    private BillingAutomationSettingsService billingAutomationSettingsService;

    @Autowired
    private InvoiceNumberService invoiceNumberService;

    @Test
    void billingCustomerList_prefersCurrentMonthInvoiceOverPreviousOverdueInvoice() throws Exception {
        Customer customer = createCustomer("active");
        CustomerServiceEntity service = createService(customer, "active", new BigDecimal("200000"));
        LocalDate currentDueDate = LocalDate.now().plusDays(2);
        createInvoice(customer, service, currentDueDate.minusMonths(1), "overdue");
        Invoice currentInvoice = createInvoice(customer, service, currentDueDate, "pending");

        projectionService.refreshCustomers(java.util.List.of(customer.getId()));

        mockMvc.perform(get("/api/billing/customers")
                        .param("search", customer.getCustomerCode())
                        .with(adminUser())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].invoiceId").value(currentInvoice.getId()))
                .andExpect(jsonPath("$.data.content[0].dueDate").value(currentDueDate.toString()));
    }

    @Test
    void billingCustomerList_filtersCustomersByExactDueDate() throws Exception {
        Customer firstCustomer = createCustomer("active");
        CustomerServiceEntity firstService = createService(firstCustomer, "active", new BigDecimal("210000"));
        LocalDate selectedDueDate = LocalDate.now().plusDays(1);
        createInvoice(firstCustomer, firstService, selectedDueDate, "pending");

        Customer secondCustomer = createCustomer("active");
        CustomerServiceEntity secondService = createService(secondCustomer, "active", new BigDecimal("220000"));
        createInvoice(secondCustomer, secondService, selectedDueDate.plusDays(1), "pending");

        projectionService.refreshCustomers(java.util.List.of(firstCustomer.getId(), secondCustomer.getId()));

        mockMvc.perform(get("/api/billing/customers")
                        .param("search", firstCustomer.getCustomerCode())
                        .param("due_date_from", selectedDueDate.toString())
                        .param("due_date_to", selectedDueDate.toString())
                        .with(adminUser())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].customerId").value(firstCustomer.getId()))
                .andExpect(jsonPath("$.data.content[0].dueDate").value(selectedDueDate.toString()));
    }

    @Test
    void projectionRefresh_ignoresNoPaymentInvoiceWhenCurrentMonthInvoiceExists() {
        Customer customer = createCustomer("suspended");
        CustomerServiceEntity service = createService(customer, "suspended", new BigDecimal("185000"));
        LocalDate currentDueDate = LocalDate.now().plusDays(3);
        createInvoice(customer, service, currentDueDate.minusMonths(1), "no_payment");
        createInvoice(customer, service, currentDueDate, "pending");

        projectionService.refreshCustomers(java.util.List.of(customer.getId()));

        CustomerBillingStatus status = customerBillingStatusRepository.findByCustomerId(customer.getId()).orElseThrow();
        int leadDays = billingAutomationSettingsService.getOrCreate().getInvoiceSendDaysBeforeDue();
        assertThat(status.getCurrentInvoiceStatus()).isEqualTo("scheduled");
        assertThat(status.getNextInvoiceSendDate()).isEqualTo(currentDueDate.minusDays(leadDays));
        assertThat(status.getOverdueDays()).isZero();
    }

    private Customer createCustomer(String status) {
        Customer customer = new Customer();
        customer.setCustomerCode("CSCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        customer.setFullName("Scheduler Customer " + UUID.randomUUID());
        customer.setPhone("08" + System.nanoTime());
        customer.setInstallationAddress("Jl. Test Scheduler");
        customer.setStatus(status);
        customer.setIsActive(!"inactive".equalsIgnoreCase(status));
        customer.setRegistrationDate(LocalDate.now().minusMonths(2));
        return customerRepository.save(customer);
    }

    private CustomerServiceEntity createService(Customer customer, String status, BigDecimal monthlyFee) {
        CustomerServiceEntity service = new CustomerServiceEntity();
        service.setCustomer(customer);
        service.setMonthlyFee(monthlyFee);
        service.setInstallationFee(BigDecimal.ZERO);
        service.setActivationDate(LocalDate.now().minusMonths(2));
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
}
