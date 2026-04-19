package com.netmaster.nmx.security;

import com.netmaster.nmx.config.TenantProvisioningProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SupportReadOnlyGuardFilter extends OncePerRequestFilter {

    private final TenantProvisioningProperties tenantProvisioningProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!tenantProvisioningProperties.isEnabled()) {
            return true;
        }
        return !TenantRouteAccessPolicy.requiresTenantContext(request.getRequestURI())
                || !TenantRouteAccessPolicy.isMutatingRequest(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        boolean readOnlySupportMode = session != null
                && Boolean.TRUE.equals(session.getAttribute(SessionAttributeKeys.SUPPORT_READ_ONLY));

        if (readOnlySupportMode) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Mode support superadmin bersifat read-only. Request write diblokir.");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
