package com.netmaster.nmx.service;

import com.netmaster.nmx.config.TenantContextHolder;
import com.netmaster.nmx.config.TenantProvisioningProperties;
import com.netmaster.nmx.config.TenantRoutingDataSource;
import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.model.TenantDatabaseInfo;
import com.netmaster.nmx.master.repository.TenantDatabaseInfoRepository;
import jakarta.annotation.PostConstruct;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.function.Supplier;

@Service
@DependsOn("masterSchemaBootstrap")
@RequiredArgsConstructor
@Slf4j
public class TenantConnectionManager {

    private final TenantDatabaseInfoRepository tenantDatabaseInfoRepository;
    private final TenantRoutingDataSource tenantRoutingDataSource;
    private final TenantProvisioningProperties provisioningProperties;

    @PostConstruct
    public void preloadKnownTenants() {
        if (!provisioningProperties.isEnabled()) {
            log.info("Tenant database routing is disabled. Operational queries stay on the primary datasource.");
            return;
        }
        if (!provisioningProperties.isPreloadActiveDataSources()) {
            log.info("Tenant datasource preload is disabled. Datasource will be registered lazily per tenant access.");
            return;
        }
        try {
            tenantDatabaseInfoRepository.findAll().stream()
                    .filter(info -> Boolean.TRUE.equals(info.getActive()))
                    .forEach(info -> registerTenant(info.getTenantId(), info));
        } catch (Exception ex) {
            log.warn("Skip tenant preload because master SaaS schema is not ready yet: {}", ex.getMessage());
        }
    }

    public void activateTenant(Long tenantId) {
        if (!provisioningProperties.isEnabled()) {
            return;
        }
        TenantDatabaseInfo info = tenantDatabaseInfoRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalStateException("Metadata koneksi tenant tidak ditemukan"));
        registerTenant(tenantId, info);
    }

    public void activateTenant(Tenant tenant) {
        activateTenant(tenant.getId());
    }

    public <T> T executeInTenantContext(Tenant tenant, Supplier<T> supplier) {
        if (!provisioningProperties.isEnabled()) {
            return supplier.get();
        }
        activateTenant(tenant);
        TenantContextHolder.setTenantKey(resolveTenantKey(tenant.getId()));
        try {
            return supplier.get();
        } finally {
            TenantContextHolder.clear();
        }
    }

    public void runInTenantContext(Tenant tenant, Runnable runnable) {
        executeInTenantContext(tenant, () -> {
            runnable.run();
            return null;
        });
    }

    public Optional<TenantDatabaseInfo> findConnection(Long tenantId) {
        return tenantDatabaseInfoRepository.findByTenantId(tenantId);
    }

    public String resolveTenantKey(Long tenantId) {
        if (!provisioningProperties.isEnabled()) {
            return "primary";
        }
        return "tenant-" + tenantId;
    }

    private void registerTenant(Long tenantId, TenantDatabaseInfo info) {
        if (!provisioningProperties.isEnabled()) {
            return;
        }
        String key = resolveTenantKey(tenantId);
        if (tenantRoutingDataSource.hasTenant(key)) {
            return;
        }
        DataSource dataSource = createTenantDataSource(tenantId, info);
        tenantRoutingDataSource.registerTenantDataSource(key, dataSource);
    }

    private DataSource createTenantDataSource(Long tenantId, TenantDatabaseInfo info) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(provisioningProperties.getDriverClassName());
        dataSource.setJdbcUrl(info.getJdbcUrl());
        dataSource.setUsername(info.getDbUser());
        dataSource.setPassword(info.getDbPasswordEncrypted());
        dataSource.setPoolName("tenant-" + tenantId + "-pool");
        dataSource.setMaximumPoolSize(safePositive(provisioningProperties.getMaxPoolSize(), 4));
        dataSource.setMinimumIdle(safeNonNegative(provisioningProperties.getMinIdle(), 0));
        dataSource.setConnectionTimeout(safePositive(provisioningProperties.getConnectionTimeoutMs(), 15000L));
        dataSource.setIdleTimeout(safePositive(provisioningProperties.getIdleTimeoutMs(), 60000L));
        dataSource.setMaxLifetime(safePositive(provisioningProperties.getMaxLifetimeMs(), 300000L));
        dataSource.setInitializationFailTimeout(-1L);
        return dataSource;
    }

    private int safePositive(Integer value, int fallback) {
        return value != null && value > 0 ? value : fallback;
    }

    private int safeNonNegative(Integer value, int fallback) {
        return value != null && value >= 0 ? value : fallback;
    }

    private long safePositive(Long value, long fallback) {
        return value != null && value > 0 ? value : fallback;
    }
}
