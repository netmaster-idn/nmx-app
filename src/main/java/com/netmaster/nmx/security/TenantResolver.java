package com.netmaster.nmx.security;

import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TenantResolver {

    private final TenantRepository tenantRepository;

    public Optional<Tenant> resolve(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Optional<Tenant> resolvedFromSupportSession = resolveBySessionIdentifier(
                    session.getAttribute(SessionAttributeKeys.SUPPORT_TENANT_ID),
                    session.getAttribute(SessionAttributeKeys.TENANT_SLUG)
            );
            if (resolvedFromSupportSession.isPresent()) {
                return resolvedFromSupportSession;
            }

            Optional<Tenant> resolvedFromTenantSession = resolveBySessionIdentifier(
                    session.getAttribute(SessionAttributeKeys.TENANT_ID),
                    session.getAttribute(SessionAttributeKeys.TENANT_SLUG)
            );
            if (resolvedFromTenantSession.isPresent()) {
                return resolvedFromTenantSession;
            }
        }

        String slug = request.getHeader("X-Tenant-Slug");
        if (!StringUtils.hasText(slug)) {
            slug = request.getParameter("tenantSlug");
        }
        if (!StringUtils.hasText(slug)) {
            slug = request.getParameter("tenant");
        }
        if (!StringUtils.hasText(slug)) {
            slug = resolveSubdomain(request);
        }
        if (!StringUtils.hasText(slug)) {
            return Optional.empty();
        }
        return tenantRepository.findBySlug(slug);
    }

    private Optional<Tenant> resolveBySessionIdentifier(Object tenantIdCandidate, Object tenantSlugCandidate) {
        if (tenantIdCandidate instanceof Number number) {
            Optional<Tenant> tenant = tenantRepository.findById(number.longValue());
            if (tenant.isPresent()) {
                return tenant;
            }
        } else if (tenantIdCandidate instanceof String tenantIdText && StringUtils.hasText(tenantIdText)) {
            try {
                Optional<Tenant> tenant = tenantRepository.findById(Long.parseLong(tenantIdText.trim()));
                if (tenant.isPresent()) {
                    return tenant;
                }
            } catch (NumberFormatException ignored) {
                // Fallback to slug resolution below when the session value is not numeric.
            }
        }

        if (tenantSlugCandidate instanceof String tenantSlug && StringUtils.hasText(tenantSlug)) {
            return tenantRepository.findBySlug(tenantSlug.trim());
        }
        return Optional.empty();
    }

    private String resolveSubdomain(HttpServletRequest request) {
        String host = request.getServerName();
        if (!StringUtils.hasText(host)) {
            return null;
        }
        String[] parts = host.split("\\.");
        return parts.length >= 3 ? parts[0] : null;
    }
}
