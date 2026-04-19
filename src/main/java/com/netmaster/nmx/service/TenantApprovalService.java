package com.netmaster.nmx.service;

import com.netmaster.nmx.config.TenantProvisioningProperties;
import com.netmaster.nmx.dto.superadmin.TenantApprovalRequest;
import com.netmaster.nmx.dto.superadmin.TenantRejectRequest;
import com.netmaster.nmx.master.model.*;
import com.netmaster.nmx.master.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantApprovalService {

    private final IspRegistrationRepository registrationRepository;
    private final TenantRepository tenantRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final TenantProvisioningService tenantProvisioningService;
    private final MasterAuditLogService auditLogService;
    private final TenantProvisioningProperties provisioningProperties;

    @Transactional("masterTransactionManager")
    public Tenant approve(Long registrationId, Long superAdminId, TenantApprovalRequest request, String requestIp) {
        IspRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Registrasi tenant tidak ditemukan."));
        if (registration.getStatus() != TenantStatus.PENDING) {
            throw new IllegalStateException("Hanya tenant PENDING yang bisa di-approve.");
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findByCode(resolvePlanCode(request, registration))
                .orElseThrow(() -> new IllegalArgumentException("Subscription plan tidak ditemukan."));

        Tenant tenant = tenantRepository.findByRegistrationId(registration.getId()).orElseGet(Tenant::new);
        tenant.setCompanyName(registration.getCompanyName());
        tenant.setSlug(registration.getSlug());
        tenant.setEmail(registration.getEmail());
        tenant.setPhone(registration.getPhone());
        tenant.setAddress(registration.getAddress());
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant.setApprovedBy(superAdminId);
        tenant.setApprovedAt(LocalDateTime.now());
        tenant.setRegistrationId(registration.getId());
        tenant.setSubscriptionPlan(plan);
        tenant.setSubscriptionStartsAt(LocalDate.now());
        tenant.setSubscriptionEndsAt(LocalDate.now().plusDays(30));
        Tenant savedTenant = tenantRepository.save(tenant);

        registration.setStatus(TenantStatus.ACTIVE);
        registration.setReviewedBy(superAdminId);
        registration.setReviewedAt(LocalDateTime.now());
        registrationRepository.save(registration);

        tenantProvisioningService.provisionTenantDatabase(
                savedTenant,
                plan,
                registration.getOwnerName(),
                registration.getOwnerEmail(),
                registration.getOwnerUsername(),
                registration.getOwnerPasswordHash()
        );

        ApprovalHistory history = new ApprovalHistory();
        history.setRegistrationId(registration.getId());
        history.setTenantId(savedTenant.getId());
        history.setAction(ApprovalAction.APPROVED);
        history.setActorType("SUPERADMIN");
        history.setActorId(superAdminId);
        history.setNotes(request != null ? request.getNotes() : null);
        approvalHistoryRepository.save(history);

        auditLogService.record(
                "SUPERADMIN",
                superAdminId,
                "TENANT_APPROVED",
                savedTenant.getId(),
                "TENANT",
                savedTenant.getId().toString(),
                Map.of("registrationId", registration.getId(), "tenantSlug", savedTenant.getSlug(), "plan", plan.getCode()),
                requestIp
        );

        return savedTenant;
    }

    @Transactional("masterTransactionManager")
    public IspRegistration reject(Long registrationId, Long superAdminId, TenantRejectRequest request, String requestIp) {
        return rejectByTenantId(null, registrationId, superAdminId, request.getReason(), requestIp);
    }

    @Transactional("masterTransactionManager")
    public IspRegistration rejectByTenantId(Long tenantId, Long registrationId, Long superAdminId, String reason, String requestIp) {
        IspRegistration registration = registrationRepository.findById(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Registrasi tenant tidak ditemukan."));
        registration.setStatus(TenantStatus.REJECTED);
        registration.setRejectionReason(reason);
        registration.setReviewedBy(superAdminId);
        registration.setReviewedAt(LocalDateTime.now());
        IspRegistration saved = registrationRepository.save(registration);

        if (tenantId != null) {
            tenantRepository.findById(tenantId).ifPresent(tenant -> {
                tenant.setStatus(TenantStatus.REJECTED);
                tenantRepository.save(tenant);
            });
        } else {
            tenantRepository.findByRegistrationId(saved.getId()).ifPresent(tenant -> {
                tenant.setStatus(TenantStatus.REJECTED);
                tenantRepository.save(tenant);
            });
        }

        ApprovalHistory history = new ApprovalHistory();
        history.setRegistrationId(saved.getId());
        history.setAction(ApprovalAction.REJECTED);
        history.setActorType("SUPERADMIN");
        history.setActorId(superAdminId);
        history.setNotes(reason);
        approvalHistoryRepository.save(history);

        auditLogService.record(
                "SUPERADMIN",
                superAdminId,
                "TENANT_REJECTED",
                tenantId,
                "ISP_REGISTRATION",
                saved.getId().toString(),
                Map.of("reason", reason, "companyName", saved.getCompanyName()),
                requestIp
        );

        return saved;
    }

    private String resolvePlanCode(TenantApprovalRequest request, IspRegistration registration) {
        if (request != null && request.getSubscriptionPlanCode() != null && !request.getSubscriptionPlanCode().isBlank()) {
            return request.getSubscriptionPlanCode().trim();
        }
        if (registration.getRequestedPlanCode() != null && !registration.getRequestedPlanCode().isBlank()) {
            return registration.getRequestedPlanCode().trim();
        }
        return provisioningProperties.getDefaultPlanCode();
    }
}
