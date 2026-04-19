package com.netmaster.nmx.service;

import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.model.TenantStatus;
import com.netmaster.nmx.master.model.TenantUserIndex;
import com.netmaster.nmx.master.repository.TenantRepository;
import com.netmaster.nmx.master.repository.TenantUserIndexRepository;
import com.netmaster.nmx.model.User;
import com.netmaster.nmx.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static com.netmaster.nmx.security.SessionAttributeKeys.TENANT_ID;
import static com.netmaster.nmx.security.SessionAttributeKeys.TENANT_SLUG;
import static com.netmaster.nmx.security.SessionAttributeKeys.TENANT_USERNAME;

@Service
@RequiredArgsConstructor
public class TenantAuthenticationService {

    private final TenantRepository tenantRepository;
    private final TenantConnectionManager tenantConnectionManager;
    private final TenantProvisioningService tenantProvisioningService;
    private final TenantUserIndexRepository tenantUserIndexRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User login(String tenantSlug, String username, String password, HttpSession session) {
        String normalizedUsername = normalizeUsername(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            throw new IllegalArgumentException("Username tenant wajib diisi.");
        }

        Tenant tenant = StringUtils.hasText(tenantSlug)
                ? resolveTenant(tenantSlug).orElseThrow(() -> new IllegalArgumentException(
                "Tenant tidak ditemukan. Gunakan slug tenant yang valid atau kosongkan slug untuk deteksi otomatis."
        ))
                : resolveTenantByCredentials(normalizedUsername, password);

        User user = authenticateTenantUser(tenant, normalizedUsername, password);
        storeTenantSession(session, tenant, user);
        return user;
    }

    private User authenticateTenantUser(Tenant tenant, String username, String password) {
        ensureTenantActive(tenant);
        tenantConnectionManager.findConnection(tenant.getId())
                .ifPresent(tenantProvisioningService::runTenantMigrations);

        User user = tenantConnectionManager.executeInTenantContext(tenant, () ->
                userRepository.findByUsername(username)
                        .orElseThrow(() -> new IllegalArgumentException("User tenant tidak ditemukan."))
        );

        if (!user.isActive()) {
            throw new IllegalStateException("User tenant tidak aktif.");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("Username atau password tenant salah.");
        }
        return user;
    }

    private Tenant resolveTenantByCredentials(String username, String password) {
        List<Tenant> candidates = resolveTenantCandidates(username);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Username tenant tidak ditemukan.");
        }

        List<Tenant> matchedTenants = new ArrayList<>();
        for (Tenant tenant : candidates) {
            Optional<User> candidateUser = findTenantUser(tenant, username);
            if (candidateUser.isEmpty()) {
                continue;
            }

            User user = candidateUser.get();
            if (user.isActive() && passwordEncoder.matches(password, user.getPassword())) {
                matchedTenants.add(tenant);
            }
        }

        if (matchedTenants.isEmpty()) {
            throw new IllegalArgumentException("Username atau password tenant salah.");
        }
        if (matchedTenants.size() > 1) {
            throw new IllegalStateException("Username ditemukan di lebih dari satu tenant. Isi tenant slug untuk memastikan login ke tenant yang benar.");
        }
        return matchedTenants.get(0);
    }

    private List<Tenant> resolveTenantCandidates(String username) {
        Set<Long> tenantIds = new LinkedHashSet<>();
        for (TenantUserIndex index : tenantUserIndexRepository.findByUsernameIgnoreCase(username)) {
            if (index.getTenantId() != null) {
                tenantIds.add(index.getTenantId());
            }
        }

        List<Tenant> candidates = new ArrayList<>();
        for (Long tenantId : tenantIds) {
            tenantRepository.findById(tenantId)
                    .filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE)
                    .ifPresent(candidates::add);
        }

        if (!candidates.isEmpty()) {
            return candidates;
        }

        for (Tenant tenant : tenantRepository.findByStatusOrderByCreatedAtDesc(TenantStatus.ACTIVE)) {
            if (findTenantUser(tenant, username).isPresent()) {
                candidates.add(tenant);
            }
        }
        return candidates;
    }

    private Optional<User> findTenantUser(Tenant tenant, String username) {
        try {
            tenantConnectionManager.findConnection(tenant.getId())
                    .ifPresent(tenantProvisioningService::runTenantMigrations);
            return tenantConnectionManager.executeInTenantContext(tenant, () -> userRepository.findByUsername(username));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private void ensureTenantActive(Tenant tenant) {
        if (tenant.getStatus() != TenantStatus.ACTIVE) {
            throw new IllegalStateException("Tenant belum aktif atau sedang dinonaktifkan.");
        }
    }

    private void storeTenantSession(HttpSession session, Tenant tenant, User user) {
        session.setAttribute(TENANT_ID, tenant.getId());
        session.setAttribute(TENANT_SLUG, tenant.getSlug());
        session.setAttribute(TENANT_USERNAME, user.getUsername());
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    private Optional<Tenant> resolveTenant(String tenantIdentifier) {
        if (tenantIdentifier == null || tenantIdentifier.isBlank()) {
            return Optional.empty();
        }

        String normalizedInput = tenantIdentifier.trim().toLowerCase(Locale.ROOT);

        return tenantRepository.findBySlug(normalizedInput)
                .or(() -> tenantRepository.findBySlug(normalizedInput.replace('_', '-')))
                .or(() -> tenantRepository.findByDbName(normalizedInput))
                .or(() -> tenantRepository.findByDbName(normalizedInput.replace('-', '_')));
    }
}
