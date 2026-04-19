package com.netmaster.nmx.security;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.service.SuperadminDeleteGuardService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class InvoicePaymentDeleteConfirmationFilter extends OncePerRequestFilter {

    private static final String CONFIRM_HEADER = "X-DELETE-CONFIRMED";
    private static final String SUPERADMIN_PASSWORD_HEADER = "X-SUPERADMIN-PASSWORD";
    private static final String TENANT_ADMIN_PASSWORD_HEADER = "X-TENANT-ADMIN-PASSWORD";

    private final SuperadminDeleteGuardService guardService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"DELETE".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri == null || !uri.matches(".*/api/invoices/\\d+/payments$");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String confirmed = request.getHeader(CONFIRM_HEADER);
        String superadminPassword = request.getHeader(SUPERADMIN_PASSWORD_HEADER);
        String tenantAdminPassword = request.getHeader(TENANT_ADMIN_PASSWORD_HEADER);

        if (!StringUtils.hasText(confirmed)
                || !StringUtils.hasText(superadminPassword)
                || !StringUtils.hasText(tenantAdminPassword)) {
            writeForbidden(response, "Masukan password superadmin dan admin tenant terlebih dahulu.");
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!guardService.isPasswordValid(superadminPassword, auth)) {
            writeForbidden(response, "Password superadmin tidak sesuai.");
            return;
        }
        if (!guardService.isTenantAdminPasswordValid(tenantAdminPassword, auth)) {
            writeForbidden(response, "Password admin tenant tidak sesuai.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiResponse<Void> payload = ApiResponse.error(message);
        response.getWriter().write(
                "{\"success\":false,\"message\":\"" + escapeJson(payload.getMessage()) + "\",\"data\":null}"
        );
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
