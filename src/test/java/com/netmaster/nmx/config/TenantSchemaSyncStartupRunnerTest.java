package com.netmaster.nmx.config;

import com.netmaster.nmx.master.model.TenantDatabaseInfo;
import com.netmaster.nmx.master.repository.TenantDatabaseInfoRepository;
import com.netmaster.nmx.service.TenantProvisioningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantSchemaSyncStartupRunnerTest {

    @Mock
    private TenantProvisioningProperties tenantProvisioningProperties;

    @Mock
    private TenantDatabaseInfoRepository tenantDatabaseInfoRepository;

    @Mock
    private TenantProvisioningService tenantProvisioningService;

    @InjectMocks
    private TenantSchemaSyncStartupRunner runner;

    @Test
    void run_syncsOnlyActiveTenantDatabases() throws Exception {
        TenantDatabaseInfo active = new TenantDatabaseInfo();
        active.setDbName("nmx_tenant_active");
        active.setActive(true);

        TenantDatabaseInfo inactive = new TenantDatabaseInfo();
        inactive.setDbName("nmx_tenant_inactive");
        inactive.setActive(false);

        when(tenantProvisioningProperties.isEnabled()).thenReturn(true);
        when(tenantDatabaseInfoRepository.findAll()).thenReturn(List.of(active, inactive));

        runner.run();

        verify(tenantProvisioningService).runTenantMigrations(active);
        verify(tenantProvisioningService, never()).runTenantMigrations(inactive);
    }
}
