package com.netmaster.nmx.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RolePermissionMiddleware extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/superadmin/tenants/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && request.getRequestURI().contains("/impersonate")
                && session.getAttribute(SessionAttributeKeys.SUPERADMIN_ID) == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Superadmin role required.");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
