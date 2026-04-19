package com.netmaster.nmx.master.repository;

import com.netmaster.nmx.master.model.TenantUserIndex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantUserIndexRepository extends JpaRepository<TenantUserIndex, Long> {
    List<TenantUserIndex> findByTenantId(Long tenantId);
    List<TenantUserIndex> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    Optional<TenantUserIndex> findByTenantIdAndUsernameIgnoreCase(Long tenantId, String username);
}
