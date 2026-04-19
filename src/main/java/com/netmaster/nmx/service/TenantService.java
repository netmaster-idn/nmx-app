package com.netmaster.nmx.service;

import com.netmaster.nmx.master.model.IspRegistration;
import com.netmaster.nmx.master.model.SuperadminActivityLog;
import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.model.TenantStatus;
import com.netmaster.nmx.master.repository.SuperadminActivityLogRepository;
import com.netmaster.nmx.master.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final TenantApprovalService tenantApprovalService;
    private final MasterAuditLogService auditLogService;
    private final SuperadminActivityLogRepository activityLogRepository;

    @Qualifier("masterJdbcTemplate")
    private final JdbcTemplate masterJdbcTemplate;

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<Tenant> getAllTenants() {
        try {
            return tenantRepository.findAll().stream()
                    .sorted((left, right) -> {
                        if (left.getCreatedAt() == null && right.getCreatedAt() == null) {
                            return 0;
                        }
                        if (left.getCreatedAt() == null) {
                            return 1;
                        }
                        if (right.getCreatedAt() == null) {
                            return -1;
                        }
                        return right.getCreatedAt().compareTo(left.getCreatedAt());
                    })
                    .toList();
        } catch (DataAccessException ex) {
            throw new IllegalStateException("Master control plane schema belum siap. Pastikan bootstrap schema master berhasil saat startup.", ex);
        }
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<Tenant> getPendingTenants() {
        return tenantRepository.findByStatusOrderByCreatedAtDesc(TenantStatus.PENDING);
    }

    @Transactional("masterTransactionManager")
    public Tenant approveTenant(Long id, Long superAdminId, String requestIp) {
        Tenant tenant = getTenantOrThrow(id);
        Long registrationId = tenant.getRegistrationId();
        if (registrationId == null) {
            throw new IllegalStateException("Tenant pending ini tidak memiliki registration_id.");
        }
        return tenantApprovalService.approve(registrationId, superAdminId, null, requestIp);
    }

    @Transactional("masterTransactionManager")
    public IspRegistration rejectTenant(Long id, Long superAdminId, String reason, String requestIp) {
        Tenant tenant = getTenantOrThrow(id);
        Long registrationId = tenant.getRegistrationId();
        if (registrationId == null) {
            throw new IllegalStateException("Tenant pending ini tidak memiliki registration_id.");
        }
        return tenantApprovalService.rejectByTenantId(id, registrationId, superAdminId, reason, requestIp);
    }

    @Transactional("masterTransactionManager")
    public Tenant suspendTenant(Long id, Long superAdminId, String requestIp) {
        Tenant tenant = getTenantOrThrow(id);
        tenant.setStatus(TenantStatus.SUSPENDED);
        Tenant saved = tenantRepository.save(tenant);
        auditLogService.record(
                "SUPERADMIN",
                superAdminId,
                "TENANT_SUSPENDED",
                saved.getId(),
                "TENANT",
                saved.getId().toString(),
                Map.of("companyName", saved.getCompanyName(), "tenantSlug", saved.getSlug()),
                requestIp
        );
        return saved;
    }

    @Transactional("masterTransactionManager")
    public Tenant activateTenant(Long id, Long superAdminId, String requestIp) {
        Tenant tenant = getTenantOrThrow(id);
        tenant.setStatus(TenantStatus.ACTIVE);
        Tenant saved = tenantRepository.save(tenant);
        auditLogService.record(
                "SUPERADMIN",
                superAdminId,
                "TENANT_ACTIVATED",
                saved.getId(),
                "TENANT",
                saved.getId().toString(),
                Map.of("companyName", saved.getCompanyName(), "tenantSlug", saved.getSlug()),
                requestIp
        );
        return saved;
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public List<SuperadminActivityLog> getRecentActivityLogs() {
        return activityLogRepository.findTop10ByOrderByCreatedAtDesc();
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public long countProvisionedDatabases() {
        Long count = masterJdbcTemplate.queryForObject(
                "select count(*) from tenant_databases where is_active = true",
                Long.class
        );
        return count == null ? 0 : count;
    }

    @Transactional(transactionManager = "masterTransactionManager", readOnly = true)
    public long countFailedProvisioning() {
        Long count = masterJdbcTemplate.queryForObject(
                """
                select count(*)
                from audit_logs
                where action = 'TENANT_PROVISIONING_FAILED'
                """,
                Long.class
        );
        return count == null ? 0 : count;
    }

    private Tenant getTenantOrThrow(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant tidak ditemukan."));
    }
}
