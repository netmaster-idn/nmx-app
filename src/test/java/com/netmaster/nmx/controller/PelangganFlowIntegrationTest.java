package com.netmaster.nmx.controller;

import com.netmaster.nmx.model.Customer;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.model.InternetPackage;
import com.netmaster.nmx.model.Odc;
import com.netmaster.nmx.model.Odp;
import com.netmaster.nmx.model.Region;
import com.netmaster.nmx.model.Server;
import com.netmaster.nmx.model.ServiceType;
import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import com.netmaster.nmx.repository.InvoiceRepository;
import com.netmaster.nmx.repository.InternetPackageRepository;
import com.netmaster.nmx.repository.OdcRepository;
import com.netmaster.nmx.repository.OdpRepository;
import com.netmaster.nmx.repository.RegionRepository;
import com.netmaster.nmx.repository.ServerRepository;
import com.netmaster.nmx.repository.ServiceTypeRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PelangganFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerServiceEntityRepository customerServiceEntityRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private InternetPackageRepository internetPackageRepository;

    @Autowired
    private ServiceTypeRepository serviceTypeRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private ServerRepository serverRepository;

    @Autowired
    private OdcRepository odcRepository;

    @Autowired
    private OdpRepository odpRepository;

    @Test
    void registerCustomerApi_allowsSkippingOptionalFields() throws Exception {
        InternetPackage internetPackage = createInternetPackage();
        ServiceType serviceType = createServiceType();
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String phone = "08123" + System.currentTimeMillis();

        String payload = """
                {
                  "fullName": "Pelanggan Uji %s",
                  "phone": "%s",
                  "email": "",
                  "ktpNumber": null,
                  "ktpAddress": "",
                  "installationAddress": "Jl. Test Registrasi",
                  "companyProfileId": null,
                  "serverId": null,
                  "odcId": null,
                  "odpId": null,
                  "odpPort": null,
                  "latitude": null,
                  "longitude": null,
                  "packageId": %d,
                  "serviceTypeId": %d,
                  "monthlyFee": null,
                  "installationFee": 0,
                  "notes": "registrasi tanpa data opsional"
                }
                """.formatted(unique, phone, internetPackage.getId(), serviceType.getId());

        mockMvc.perform(post("/pelanggan/api/register")
                        .with(adminUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.phone").value(phone));

        Customer customer = customerRepository.findByPhone(phone).orElseThrow();
        CustomerServiceEntity service = customerServiceEntityRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .orElseThrow();

        assertThat(customer.getKtpNumber()).isNull();
        assertThat(customer.getRegion()).isNull();
        assertThat(customer.getLatitude()).isNull();
        assertThat(customer.getLongitude()).isNull();
        assertThat(service.getOdp()).isNull();
        assertThat(service.getOdpPort()).isNull();
        assertThat(service.getMonthlyFee()).isEqualByComparingTo(internetPackage.getPrice());
    }

    @Test
    void updateCustomerBasic_allowsFillingOptionalFieldsLater() throws Exception {
        InternetPackage internetPackage = createInternetPackage();
        ServiceType serviceType = createServiceType();
        Region region = createRegion();
        Server server = createServer(region);
        Odc odc = createOdc(server);
        Odp odp = createOdp(odc);
        String phone = "08234" + System.currentTimeMillis();

        Customer customer = registerCustomer(phone, internetPackage, serviceType);

        String payload = """
                {
                  "fullName": "Pelanggan Update Lengkap",
                  "phone": "%s",
                  "email": "update@test.local",
                  "installationAddress": "Jl. Update Pelanggan",
                  "ktpNumber": "6471123412340001",
                  "companyProfileId": null,
                  "serverId": %d,
                  "odcId": %d,
                  "odpId": %d,
                  "odpPort": 3,
                  "latitude": "-0.50000000",
                  "longitude": "117.13000000"
                }
                """.formatted(phone, server.getId(), odc.getId(), odp.getId());

        mockMvc.perform(patch("/pelanggan/api/customers/{id}/basic", customer.getId())
                        .with(adminUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.phone").value(phone));

        Customer updatedCustomer = customerRepository.findById(customer.getId()).orElseThrow();
        CustomerServiceEntity updatedService = customerServiceEntityRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .orElseThrow();
        Optional<Odp> updatedOdp = odpRepository.findById(odp.getId());

        assertThat(updatedCustomer.getKtpNumber()).isEqualTo("6471123412340001");
        assertThat(updatedCustomer.getRegion()).isNotNull();
        assertThat(updatedCustomer.getRegion().getId()).isEqualTo(region.getId());
        assertThat(updatedCustomer.getLatitude()).isEqualByComparingTo(new BigDecimal("-0.50000000"));
        assertThat(updatedCustomer.getLongitude()).isEqualByComparingTo(new BigDecimal("117.13000000"));
        assertThat(updatedService.getOdp()).isNotNull();
        assertThat(updatedService.getOdp().getId()).isEqualTo(odp.getId());
        assertThat(updatedService.getOdpPort()).isEqualTo(3);
        assertThat(updatedOdp).isPresent();
        assertThat(updatedOdp.get().getUsedPort()).isEqualTo(1);
    }

    @Test
    void verifyCustomerActivation_usesActivationModalPayloadForActivationDateAndPaymentMethod() throws Exception {
        InternetPackage internetPackage = createInternetPackage();
        ServiceType serviceType = createServiceType();
        String phone = "08345" + System.currentTimeMillis();
        BigDecimal installationFee = new BigDecimal("250000");
        LocalDate activationDate = LocalDate.of(2026, 3, 17);

        Customer customer = registerCustomer(phone, internetPackage, serviceType, installationFee);

        String payload = """
                {
                  "installationFee": 250000,
                  "paymentMethod": "Transfer",
                  "activationDate": "2026-03-17"
                }
                """;

        mockMvc.perform(post("/pelanggan/api/customers/{id}/verify", customer.getId())
                        .with(adminUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentMethod").value("Transfer"))
                .andExpect(jsonPath("$.data.paymentDate").value("2026-03-17"))
                .andExpect(jsonPath("$.data.installationFee").value(250000))
                .andExpect(jsonPath("$.data.monthlyFee").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(250000))
                .andExpect(jsonPath("$.data.status").value("paid"));

        Customer updatedCustomer = customerRepository.findById(customer.getId()).orElseThrow();
        CustomerServiceEntity updatedService = customerServiceEntityRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .orElseThrow();

        assertThat(updatedCustomer.getStatus()).isEqualTo("active");
        assertThat(updatedService.getStatus()).isEqualTo("active");
        assertThat(updatedService.getActivationDate()).isEqualTo(activationDate);
        assertThat(updatedService.getInstallationFee()).isEqualByComparingTo(installationFee);

        var activationInvoice = invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customer.getId()).stream()
                .filter(invoice -> activationDate.equals(invoice.getPaymentDate()))
                .findFirst()
                .orElseThrow();
        assertThat(activationInvoice.getInvoiceType()).isEqualTo("activation");
        assertThat(activationInvoice.getPaymentMethod()).isEqualTo("Transfer");
        assertThat(activationInvoice.getPaymentDate()).isEqualTo(activationDate);
        assertThat(activationInvoice.getMonthlyFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(activationInvoice.getInstallationFee()).isEqualByComparingTo(installationFee);
        assertThat(activationInvoice.getTotalAmount()).isEqualByComparingTo(installationFee);

        var nextSubscriptionInvoice = invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customer.getId()).stream()
                .filter(invoice -> "subscription".equalsIgnoreCase(invoice.getInvoiceType()))
                .findFirst()
                .orElseThrow();
        assertThat(nextSubscriptionInvoice.getMonthlyFee()).isEqualByComparingTo(internetPackage.getPrice());
        assertThat(nextSubscriptionInvoice.getInstallationFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(nextSubscriptionInvoice.getTotalAmount()).isEqualByComparingTo(internetPackage.getPrice());
    }

    private Customer registerCustomer(String phone, InternetPackage internetPackage, ServiceType serviceType) throws Exception {
        return registerCustomer(phone, internetPackage, serviceType, BigDecimal.ZERO);
    }

    private Customer registerCustomer(String phone, InternetPackage internetPackage, ServiceType serviceType, BigDecimal installationFee) throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        String payload = """
                {
                  "fullName": "Pelanggan Awal %s",
                  "phone": "%s",
                  "installationAddress": "Jl. Awal",
                  "packageId": %d,
                  "serviceTypeId": %d,
                  "installationFee": %s
                }
                """.formatted(unique, phone, internetPackage.getId(), serviceType.getId(), installationFee.toPlainString());

        mockMvc.perform(post("/pelanggan/api/register")
                        .with(adminUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        return customerRepository.findByPhone(phone).orElseThrow();
    }

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor adminUser() {
        return user("integration-admin").authorities(() -> "ROLE_SUPER_ADMIN");
    }

    private InternetPackage createInternetPackage() {
        InternetPackage internetPackage = new InternetPackage();
        internetPackage.setName("PKG-TEST-" + UUID.randomUUID());
        internetPackage.setPrice(new BigDecimal("175000"));
        internetPackage.setSpeedDown(50);
        internetPackage.setSpeedUp(20);
        internetPackage.setIsActive(true);
        return internetPackageRepository.save(internetPackage);
    }

    private ServiceType createServiceType() {
        Optional<ServiceType> existing = serviceTypeRepository.findByNameIgnoreCase("Dedicated BW");
        if (existing.isPresent()) {
            return existing.get();
        }

        ServiceType serviceType = new ServiceType();
        serviceType.setName("Dedicated BW");
        serviceType.setServiceType("INTERNET");
        serviceType.setSupportPppoe(true);
        serviceType.setIsActive(true);
        return serviceTypeRepository.save(serviceType);
    }

    private Region createRegion() {
        Region region = new Region();
        region.setName("Region Test " + UUID.randomUUID());
        region.setCode("REG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return regionRepository.save(region);
    }

    private Server createServer(Region region) {
        Server server = new Server();
        server.setName("SRV-TEST-" + UUID.randomUUID());
        server.setIpAddress("10.10.10.10");
        server.setRegion(region);
        server.setIsActive(true);
        return serverRepository.save(server);
    }

    private Odc createOdc(Server server) {
        Odc odc = new Odc();
        odc.setName("ODC-TEST-" + UUID.randomUUID());
        odc.setServer(server);
        odc.setCapacity(16);
        odc.setUsedPort(0);
        odc.setIsActive(true);
        return odcRepository.save(odc);
    }

    private Odp createOdp(Odc odc) {
        Odp odp = new Odp();
        odp.setName("ODP-TEST-" + UUID.randomUUID());
        odp.setOdc(odc);
        odp.setNodeType("ODP");
        odp.setCapacity(8);
        odp.setUsedPort(0);
        odp.setIsActive(true);
        return odpRepository.save(odp);
    }
}
