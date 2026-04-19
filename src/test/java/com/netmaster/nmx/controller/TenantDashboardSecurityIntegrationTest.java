package com.netmaster.nmx.controller;

import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.model.TenantStatus;
import com.netmaster.nmx.master.repository.TenantRepository;
import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.InvoiceRepository;
import com.netmaster.nmx.repository.NetworkDeviceRepository;
import com.netmaster.nmx.repository.TicketRepository;
import com.netmaster.nmx.service.TenantConnectionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.netmaster.nmx.security.SessionAttributeKeys.TENANT_ID;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantDashboardSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private TenantConnectionManager tenantConnectionManager;

    @MockBean
    private CustomerRepository customerRepository;

    @MockBean
    private InvoiceRepository invoiceRepository;

    @MockBean
    private TicketRepository ticketRepository;

    @MockBean
    private NetworkDeviceRepository networkDeviceRepository;

    @Test
    void tenantDashboard_withTenantSession_returnsDashboardPayload() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setId(77L);
        tenant.setSlug("wifi-bersama");
        tenant.setCompanyName("Wifi Bersama");
        tenant.setStatus(TenantStatus.ACTIVE);

        when(tenantRepository.findById(77L)).thenReturn(Optional.of(tenant));
        doNothing().when(tenantConnectionManager).activateTenant(tenant);
        when(customerRepository.count()).thenReturn(12L);
        when(invoiceRepository.count()).thenReturn(7L);
        when(ticketRepository.count()).thenReturn(3L);
        when(networkDeviceRepository.count()).thenReturn(5L);

        mockMvc.perform(get("/tenant/api/dashboard").sessionAttr(TENANT_ID, 77L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tenantSlug").value("wifi-bersama"))
                .andExpect(jsonPath("$.data.tenantName").value("Wifi Bersama"))
                .andExpect(jsonPath("$.data.totalCustomers").value(12))
                .andExpect(jsonPath("$.data.totalInvoices").value(7))
                .andExpect(jsonPath("$.data.totalTickets").value(3))
                .andExpect(jsonPath("$.data.totalDevices").value(5));
    }
}
