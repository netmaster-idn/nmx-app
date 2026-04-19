package com.netmaster.nmx.master.repository;

import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.model.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findBySlug(String slug);
    Optional<Tenant> findByDbName(String dbName);
    Optional<Tenant> findByEmail(String email);
    Optional<Tenant> findByRegistrationId(Long registrationId);
    List<Tenant> findByStatusOrderByCreatedAtDesc(TenantStatus status);
    long countByStatus(TenantStatus status);
}
