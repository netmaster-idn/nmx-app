package com.netmaster.nmx.master.repository;

import com.netmaster.nmx.master.model.TenantDatabaseInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantDatabaseInfoRepository extends JpaRepository<TenantDatabaseInfo, Long> {
    Optional<TenantDatabaseInfo> findByTenantId(Long tenantId);
    Optional<TenantDatabaseInfo> findByDbName(String dbName);
}
