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
public class TenantAuthMiddleware extends OncePerRequestFilter {

    private final TenantProvisioningProperties tenantProvisioningProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!tenantProvisioningProperties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return !path.startsWith("/tenant") || path.equals("/tenant/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SessionAttributeKeys.TENANT_ID) == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Tenant authentication required.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
