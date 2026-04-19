package com.netmaster.nmx.service;

import com.netmaster.nmx.dto.superadmin.TenantDirectoryResponse;
import com.netmaster.nmx.dto.superadmin.TenantSummaryResponse;
import com.netmaster.nmx.dto.superadmin.TenantUpdateRequest;
import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.model.TenantStatus;
import com.netmaster.nmx.master.repository.SubscriptionPlanRepository;
import com.netmaster.nmx.master.repository.TenantRepository;
import com.netmaster.nmx.repository.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static com.netmaster.nmx.security.SessionAttributeKeys.SUPPORT_READ_ONLY;
import static com.netmaster.nmx.security.SessionAttributeKeys.SUPPORT_TENANT_ID;

@Service
@RequiredArgsConstructor
public class SuperAdminTenantAccessService {

    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final TenantConnectionManager tenantConnectionManager;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final TicketRepository ticketRepository;
    private final NetworkDeviceRepository networkDeviceRepository;
    private final ProjectRecordRepository projectRecordRepository;
    private final MasterAuditLogService auditLogService;
    @Qualifier("masterJdbcTemplate")
    private final JdbcTemplate masterJdbcTemplate;

    @Transactional("masterTransactionManager")
    public List<TenantDirectoryResponse> findAllTenants() {
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
                .map(this::toDirectoryResponse)
                .toList();
    }

    @Transactional("masterTransactionManager")
    public List<TenantDirectoryResponse> findDeletedTenants() {
        return masterJdbcTemplate.query(
                """
                select id,
                       company_name,
                       slug,
                       email,
                       phone,
                       status,
                       subscription_ends_at,
                       approved_at,
                       created_at,
                       deleted_at
                from tenants
                where deleted_at is not null
                order by deleted_at desc, id desc
                """,
                (rs, rowNum) -> toDeletedDirectoryResponse(rs)
        );
    }

    @Transactional("masterTransactionManager")
    public List<TenantDirectoryResponse> findByStatus(TenantStatus status) {
        return tenantRepository.findByStatusOrderByCreatedAtDesc(status).stream().map(this::toDirectoryResponse).toList();
    }

    @Transactional("masterTransactionManager")
    public Tenant updateTenant(Long tenantId, TenantUpdateRequest request) {
        Tenant tenant = getTenant(tenantId);
        if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
            tenant.setCompanyName(request.getCompanyName().trim());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            tenant.setEmail(request.getEmail().trim());
        }
        if (request.getPhone() != null) {
            tenant.setPhone(request.getPhone().trim());
        }
        if (request.getAddress() != null) {
            tenant.setAddress(request.getAddress());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            tenant.setStatus(TenantStatus.valueOf(request.getStatus().trim().toUpperCase()));
        }
        if (request.getSubscriptionPlanCode() != null && !request.getSubscriptionPlanCode().isBlank()) {
            tenant.setSubscriptionPlan(subscriptionPlanRepository.findByCode(request.getSubscriptionPlanCode().trim())
                    .orElseThrow(() -> new IllegalArgumentException("Subscription plan tidak ditemukan.")));
        }
        return tenantRepository.save(tenant);
    }

    @Transactional("masterTransactionManager")
    public void softDeleteTenant(Long tenantId) {
        tenantRepository.deleteById(tenantId);
    }

    @Transactional("masterTransactionManager")
    public void restoreTenant(Long tenantId, Long superAdminId, String requestIp) {
        Map<String, Object> snapshot = masterJdbcTemplate.queryForMap(
                """
                select id, company_name, slug, registration_id, approved_at
                from tenants
                where id = ? and deleted_at is not null
                """,
                tenantId
        );

        TenantStatus restoredStatus = determineRestoredStatus(snapshot.get("approved_at"));
        int updated = masterJdbcTemplate.update(
                """
                update tenants
                set deleted_at = null,
                    status = ?,
                    updated_at = current_timestamp
                where id = ?
                  and deleted_at is not null
                """,
                restoredStatus.name(),
                tenantId
        );

        if (updated == 0) {
            throw new IllegalArgumentException("Tenant recycle bin tidak ditemukan.");
        }

        auditLogService.record(
                "SUPERADMIN",
                superAdminId,
                "TENANT_RESTORED",
                tenantId,
                "TENANT",
                tenantId.toString(),
                Map.of(
                        "companyName", String.valueOf(snapshot.get("company_name")),
                        "tenantSlug", String.valueOf(snapshot.get("slug")),
                        "restoredStatus", restoredStatus.name()
                ),
                requestIp
        );
    }

    public TenantSummaryResponse getSummary(Long tenantId) {
        Tenant tenant = getTenant(tenantId);
        return tenantConnectionManager.executeInTenantContext(tenant, () -> TenantSummaryResponse.builder()
                .tenantId(tenant.getId())
                .tenantSlug(tenant.getSlug())
                .totalCustomers(customerRepository.count())
                .totalInvoices(invoiceRepository.count())
                .totalTickets(ticketRepository.count())
                .totalDevices(networkDeviceRepository.count())
                .totalProjects(projectRecordRepository.count())
                .subscriptionPlan(tenant.getSubscriptionPlan() != null ? tenant.getSubscriptionPlan().getCode() : null)
                .tenantStatus(tenant.getStatus().name())
                .build());
    }

    public Tenant openSupportContext(Long tenantId, HttpSession session, Long superAdminId, String requestIp) {
        Tenant tenant = getTenant(tenantId);
        session.setAttribute(SUPPORT_TENANT_ID, tenant.getId());
        session.setAttribute(SUPPORT_READ_ONLY, Boolean.TRUE);
        auditLogService.record(
                "SUPERADMIN",
                superAdminId,
                "TENANT_SUPPORT_CONTEXT_OPENED",
                tenant.getId(),
                "TENANT",
                tenant.getId().toString(),
                Map.of("mode", "READ_ONLY", "tenantSlug", tenant.getSlug()),
                requestIp
        );
        return tenant;
    }

    public Tenant getTenant(Long tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant tidak ditemukan."));
    }

    private TenantDirectoryResponse toDirectoryResponse(Tenant tenant) {
        return TenantDirectoryResponse.builder()
                .id(tenant.getId())
                .companyName(tenant.getCompanyName())
                .slug(tenant.getSlug())
                .email(tenant.getEmail())
                .phone(tenant.getPhone())
                .status(tenant.getStatus().name())
                .subscriptionPlan(tenant.getSubscriptionPlan() != null ? tenant.getSubscriptionPlan().getCode() : null)
                .subscriptionEndsAt(tenant.getSubscriptionEndsAt())
                .approvedAt(tenant.getApprovedAt())
                .createdAt(tenant.getCreatedAt())
                .deletedAt(tenant.getDeletedAt())
                .build();
    }

    private TenantDirectoryResponse toDeletedDirectoryResponse(ResultSet rs) throws SQLException {
        return TenantDirectoryResponse.builder()
                .id(rs.getLong("id"))
                .companyName(rs.getString("company_name"))
                .slug(rs.getString("slug"))
                .email(rs.getString("email"))
                .phone(rs.getString("phone"))
                .status(rs.getString("status"))
                .subscriptionEndsAt(rs.getObject("subscription_ends_at", java.time.LocalDate.class))
                .approvedAt(toLocalDateTime(rs.getTimestamp("approved_at")))
                .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                .deletedAt(toLocalDateTime(rs.getTimestamp("deleted_at")))
                .build();
    }

    private TenantStatus determineRestoredStatus(Object approvedAt) {
        return approvedAt instanceof Timestamp || approvedAt instanceof java.time.LocalDateTime
                ? TenantStatus.ACTIVE
                : TenantStatus.PENDING;
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
