package com.netmaster.nmx.service;

import com.netmaster.nmx.dto.tenant.RegisterTenantRequest;
import com.netmaster.nmx.master.model.ApprovalAction;
import com.netmaster.nmx.master.model.IspRegistration;
import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.model.TenantStatus;
import com.netmaster.nmx.master.repository.ApprovalHistoryRepository;
import com.netmaster.nmx.master.repository.IspRegistrationRepository;
import com.netmaster.nmx.master.repository.TenantRepository;
import com.netmaster.nmx.master.repository.TenantUserIndexRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenantRegistrationService {

    private final IspRegistrationRepository registrationRepository;
    private final TenantRepository tenantRepository;
    private final ApprovalHistoryRepository approvalHistoryRepository;
    private final TenantUserIndexRepository tenantUserIndexRepository;
    private final PasswordEncoder passwordEncoder;
    private final MasterAuditLogService auditLogService;

    @Transactional("masterTransactionManager")
    public IspRegistration register(RegisterTenantRequest request, String requestIp) {
        registrationRepository.findByEmail(request.getEmail()).ifPresent(existing -> {
            throw new IllegalArgumentException("Email perusahaan sudah terdaftar.");
        });
        validatePasswordConfirmation(request);
        validateOwnerUsername(request.getOwnerUsername());

        IspRegistration registration = new IspRegistration();
        registration.setCompanyName(request.getCompanyName().trim());
        registration.setSlug(generateSlug(request.getCompanyName()));
        registration.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        registration.setPhone(request.getPhone().trim());
        registration.setAddress(request.getAddress());
        registration.setOwnerName(request.getOwnerName().trim());
        registration.setOwnerEmail(request.getOwnerEmail().trim().toLowerCase(Locale.ROOT));
        registration.setOwnerUsername(request.getOwnerUsername().trim());
        registration.setOwnerPasswordHash(passwordEncoder.encode(request.getPassword()));
        registration.setRequestedPlanCode(request.getRequestedPlanCode());
        registration.setStatus(TenantStatus.PENDING);

        IspRegistration saved = registrationRepository.save(registration);
        syncPendingTenant(saved);

        com.netmaster.nmx.master.model.ApprovalHistory history = new com.netmaster.nmx.master.model.ApprovalHistory();
        history.setRegistrationId(saved.getId());
        history.setAction(ApprovalAction.SUBMITTED);
        history.setActorType("TENANT_REGISTRATION");
        history.setNotes("New tenant registration submitted.");
        approvalHistoryRepository.save(history);

        auditLogService.record(
                "TENANT_REGISTRATION",
                saved.getId(),
                "TENANT_REGISTERED",
                null,
                "ISP_REGISTRATION",
                saved.getId().toString(),
                Map.of("companyName", saved.getCompanyName(), "status", saved.getStatus().name()),
                requestIp
        );

        return saved;
    }

    private void validatePasswordConfirmation(RegisterTenantRequest request) {
        String password = request.getPassword() == null ? "" : request.getPassword();
        String confirmPassword = request.getConfirmPassword() == null ? "" : request.getConfirmPassword();
        if (!password.equals(confirmPassword)) {
            throw new IllegalArgumentException("Password dan repeat password tidak sama.");
        }
        validatePasswordStrength(password);
    }

    private void validatePasswordStrength(String password) {
        if (password.length() < 8) {
            throw new IllegalArgumentException("Password minimal 8 karakter.");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Password harus memiliki minimal 1 huruf besar.");
        }
        if (!password.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalArgumentException("Password harus memiliki minimal 1 karakter spesial.");
        }
    }

    private void validateOwnerUsername(String ownerUsername) {
        String normalizedUsername = ownerUsername == null ? "" : ownerUsername.trim();
        if (normalizedUsername.isBlank()) {
            return;
        }

        registrationRepository.findByOwnerUsernameIgnoreCase(normalizedUsername).ifPresent(existing -> {
            throw new IllegalArgumentException("Username sudah digunakan pada registrasi tenant lain dan tidak bisa dipakai lagi.");
        });

        if (tenantUserIndexRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new IllegalArgumentException("Username sudah digunakan oleh tenant lain dan tidak bisa dipakai untuk registrasi.");
        }
    }

    private void syncPendingTenant(IspRegistration registration) {
        Tenant tenant = tenantRepository.findByRegistrationId(registration.getId()).orElseGet(Tenant::new);
        tenant.setCompanyName(registration.getCompanyName());
        tenant.setSlug(registration.getSlug());
        tenant.setEmail(registration.getEmail());
        tenant.setPhone(registration.getPhone());
        tenant.setAddress(registration.getAddress());
        if (tenant.getId() == null || tenant.getStatus() == null || tenant.getStatus() == TenantStatus.PENDING) {
            tenant.setStatus(TenantStatus.PENDING);
        }
        tenant.setRegistrationId(registration.getId());
        tenantRepository.save(tenant);
    }

    private String generateSlug(String value) {
        String base = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        String slug = base;
        int counter = 1;
        while (registrationRepository.findBySlug(slug).isPresent()) {
            counter++;
            slug = base + "-" + counter;
        }
        return slug;
    }
}
