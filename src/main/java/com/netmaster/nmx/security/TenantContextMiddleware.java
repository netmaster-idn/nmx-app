package com.netmaster.nmx.security;

import com.netmaster.nmx.config.TenantProvisioningProperties;
import com.netmaster.nmx.config.TenantContextHolder;
import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.service.TenantConnectionManager;
import com.netmaster.nmx.service.TenantProvisioningService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TenantContextMiddleware extends OncePerRequestFilter {
    private final TenantProvisioningProperties tenantProvisioningProperties;
    private final TenantResolver tenantResolver;
    private final TenantConnectionManager tenantConnectionManager;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!tenantProvisioningProperties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        return TenantRouteAccessPolicy.isPublicPath(path) || TenantRouteAccessPolicy.isStaticAsset(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Optional<Tenant> resolvedTenant = tenantResolver.resolve(request);
            if (hasTenantScopedSession(request) && resolvedTenant.isEmpty()) {
                clearTenantSession(request);
                redirectToLoginOrSendUnauthorized(request, response, "Tenant context tidak ditemukan.");
                return;
            }

            resolvedTenant.ifPresent(tenant -> {
                tenantConnectionManager.activateTenant(tenant);
                TenantContextHolder.setTenantKey(tenantConnectionManager.resolveTenantKey(tenant.getId()));
                request.setAttribute("resolvedTenant", tenant);
            });
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
        }
    }

    private boolean hasTenantScopedSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        return session.getAttribute(SessionAttributeKeys.TENANT_ID) != null
                || session.getAttribute(SessionAttributeKeys.SUPPORT_TENANT_ID) != null;
    }

    private void clearTenantSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        session.removeAttribute(SessionAttributeKeys.TENANT_ID);
        session.removeAttribute(SessionAttributeKeys.TENANT_SLUG);
        session.removeAttribute(SessionAttributeKeys.TENANT_USERNAME);
        session.removeAttribute(SessionAttributeKeys.TENANT_SCHEMA_SYNCED);
        session.removeAttribute(SessionAttributeKeys.SUPPORT_TENANT_ID);
        session.removeAttribute(SessionAttributeKeys.SUPPORT_READ_ONLY);
    }

    private void redirectToLoginOrSendUnauthorized(HttpServletRequest request,
                                                   HttpServletResponse response,
                                                   String message) throws IOException {
        if (acceptsHtml(request)) {
            response.sendRedirect("/login?tenantRequired=1");
            return;
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, message);
    }

    private boolean acceptsHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept == null || accept.contains("text/html");
    }

}
