package com.netmaster.nmx.config;

import lombok.Setter;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Setter
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

    private final Map<Object, Object> resolvedDataSources = new ConcurrentHashMap<>();

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContextHolder.getTenantKey();
    }

    public synchronized void registerTenantDataSource(String tenantKey, DataSource dataSource) {
        resolvedDataSources.put(tenantKey, dataSource);
        super.setTargetDataSources(resolvedDataSources);
        super.afterPropertiesSet();
    }

    public boolean hasTenant(String tenantKey) {
        return resolvedDataSources.containsKey(tenantKey);
    }
}
