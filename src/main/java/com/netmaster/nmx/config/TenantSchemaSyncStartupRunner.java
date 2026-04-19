package com.netmaster.nmx.config;

import com.netmaster.nmx.master.model.TenantDatabaseInfo;
import com.netmaster.nmx.master.repository.TenantDatabaseInfoRepository;
import com.netmaster.nmx.service.TenantProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "nmx.tenant.startup-migration.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class TenantSchemaSyncStartupRunner implements CommandLineRunner {

    private final TenantProvisioningProperties tenantProvisioningProperties;
    private final TenantDatabaseInfoRepository tenantDatabaseInfoRepository;
    private final TenantProvisioningService tenantProvisioningService;

    @Override
    public void run(String... args) {
        if (!tenantProvisioningProperties.isEnabled()) {
            log.info("Tenant schema sync skipped because tenant mode is disabled.");
            return;
        }
        for (TenantDatabaseInfo connection : tenantDatabaseInfoRepository.findAll()) {
            if (connection.getActive() == null || !connection.getActive()) {
                continue;
            }

            try {
                tenantProvisioningService.runTenantMigrations(connection);
                log.info("Tenant schema synced for tenant database {}", connection.getDbName());
            } catch (Exception ex) {
                log.warn("Failed to sync tenant schema for {}: {}", connection.getDbName(), ex.getMessage());
            }
        }
    }
}
