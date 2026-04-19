package com.netmaster.nmx.security;

import com.netmaster.nmx.config.TenantProvisioningProperties;
import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.model.TenantStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class EnsureTenantIsActive extends OncePerRequestFilter {

    private final TenantProvisioningProperties tenantProvisioningProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!tenantProvisioningProperties.isEnabled()) {
            return true;
        }
        return !TenantRouteAccessPolicy.requiresTenantContext(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Object resolvedTenant = request.getAttribute("resolvedTenant");
        if (resolvedTenant instanceof Tenant tenant && tenant.getStatus() != TenantStatus.ACTIVE) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant is not active.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
