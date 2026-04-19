package com.netmaster.nmx.security;

import com.netmaster.nmx.config.TenantProvisioningProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class TenantScopeEnforcementFilter extends OncePerRequestFilter {

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
        if (request.getAttribute("resolvedTenant") != null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!hasApplicationSessionOrAuthentication(request)) {
            if (acceptsHtml(request)) {
                response.sendRedirect("/login?tenantRequired=1");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (acceptsHtml(request)) {
            response.sendRedirect("/login?tenantRequired=1");
            return;
        }

        response.sendError(HttpServletResponse.SC_CONFLICT,
                "Tenant context wajib dipilih sebelum mengakses modul operasional.");
    }

    private boolean hasApplicationSessionOrAuthentication(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            if (session.getAttribute(SessionAttributeKeys.TENANT_ID) != null
                    || session.getAttribute(SessionAttributeKeys.SUPPORT_TENANT_ID) != null
                    || session.getAttribute(SessionAttributeKeys.SUPERADMIN_ID) != null) {
                return true;
            }
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private boolean acceptsHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept == null || accept.contains("text/html");
    }
}
