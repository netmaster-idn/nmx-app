package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.DashboardHomeViewDTO;
import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.model.TenantStatus;
import com.netmaster.nmx.master.repository.TenantRepository;
import com.netmaster.nmx.repository.AppActivityLogRepository;
import com.netmaster.nmx.service.DashboardHomeService;
import com.netmaster.nmx.service.TenantConnectionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.netmaster.nmx.security.SessionAttributeKeys.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantDashboardHomeActivityLogResilienceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private TenantConnectionManager tenantConnectionManager;

    @MockBean
    private DashboardHomeService dashboardHomeService;

    @MockBean
    private AppActivityLogRepository appActivityLogRepository;

    @Test
    void dashboardHome_withTenantSession_staysSuccessfulWhenActivityLogSaveFails() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setId(77L);
        tenant.setSlug("wifi-bersama");
        tenant.setCompanyName("Wifi Bersama");
        tenant.setStatus(TenantStatus.ACTIVE);

        when(tenantRepository.findById(77L)).thenReturn(Optional.of(tenant));
        doNothing().when(tenantConnectionManager).activateTenant(tenant);
        when(tenantConnectionManager.resolveTenantKey(77L)).thenReturn("tenant-77");
        when(dashboardHomeService.buildDashboard()).thenReturn(buildDashboardView());
        doThrow(new RuntimeException("tenant activity log table is missing"))
                .when(appActivityLogRepository)
                .save(any());

        mockMvc.perform(get("/dashboard/home")
                        .sessionAttr(TENANT_ID, 77L)
                        .with(adminUser()))
                .andExpect(status().isOk());

        verify(appActivityLogRepository).save(any());
    }

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor adminUser() {
        return user("tenant-admin").authorities(() -> "ROLE_ADMIN");
    }

    private DashboardHomeViewDTO buildDashboardView() {
        DashboardHomeViewDTO view = new DashboardHomeViewDTO();
        view.setAlerts(java.util.List.of(
                new DashboardHomeViewDTO.AlertItem("critical", "critical", "fas fa-triangle-exclamation", "0 alert kritis aktif", "Regression test"),
                new DashboardHomeViewDTO.AlertItem("warning", "warning", "fas fa-circle-exclamation", "0 invoice overdue", "Regression test"),
                new DashboardHomeViewDTO.AlertItem("info", "info", "fas fa-network-wired", "0 perangkat offline", "Regression test"),
                new DashboardHomeViewDTO.AlertItem("info", "info", "fas fa-clock", "0 issue sedang dipantau", "Regression test")
        ));
        view.setCustomerSummary(new DashboardHomeViewDTO.CustomerSummary(12, 10, 1, 1));
        view.setRevenueSummary(new DashboardHomeViewDTO.RevenueSummary("Rp 0", "Tenant summary", 0, 0, "Rp 0", "Rp 0"));
        view.setDeviceSummary(new DashboardHomeViewDTO.DeviceSummary(3, 3, 0, 0, 0));
        view.setOntSummary(new DashboardHomeViewDTO.OntSummary(4, 4, 0, 0, 0));
        view.setTicketSummary(new DashboardHomeViewDTO.TicketSummary(1, 0, 1, 0));
        view.setHealthSummary(new DashboardHomeViewDTO.HealthSummary("100%", "0%", "0%"));
        view.setMapSummary(new DashboardHomeViewDTO.MapSummary(10, 1, 1));
        view.setHighlightSummary(new DashboardHomeViewDTO.HighlightSummary("Tenant", "Wifi Bersama", "Regression test"));
        return view;
    }
}
