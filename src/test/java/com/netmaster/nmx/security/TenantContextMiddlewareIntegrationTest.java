package com.netmaster.nmx.security;

import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.model.TenantStatus;
import com.netmaster.nmx.master.repository.TenantRepository;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantContextMiddlewareIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private TenantConnectionManager tenantConnectionManager;

    @Test
    void monitoringAlert_withTenantSession_activatesTenantContext() throws Exception {
        Tenant tenant = new Tenant();
        tenant.setId(77L);
        tenant.setSlug("wifi-bersama");
        tenant.setCompanyName("Wifi Bersama");
        tenant.setStatus(TenantStatus.ACTIVE);

        when(tenantRepository.findById(77L)).thenReturn(Optional.of(tenant));
        doNothing().when(tenantConnectionManager).activateTenant(tenant);
        when(tenantConnectionManager.resolveTenantKey(77L)).thenReturn("tenant-77");

        mockMvc.perform(get("/monitoring/alert")
                        .sessionAttr(TENANT_ID, 77L)
                        .with(adminUser()))
                .andExpect(status().isOk());

        verify(tenantConnectionManager).activateTenant(tenant);
        verify(tenantConnectionManager).resolveTenantKey(77L);
    }

    @Test
    void monitoringAlert_withBrokenTenantSession_isRejectedBeforeFallback() throws Exception {
        when(tenantRepository.findById(77L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/monitoring/alert")
                        .sessionAttr(TENANT_ID, 77L)
                        .with(adminUser()))
                .andExpect(status().isUnauthorized());
    }

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor adminUser() {
        return user("tenant-admin").authorities(() -> "ROLE_ADMIN");
    }
}
