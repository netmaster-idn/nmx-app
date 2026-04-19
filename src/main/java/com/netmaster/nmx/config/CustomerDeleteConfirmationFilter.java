package com.netmaster.nmx.config;

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
public class CustomerDeleteConfirmationFilter extends OncePerRequestFilter {

    private static final String PASSWORD_HEADER = "X-SUPERADMIN-PASSWORD";
    private static final String CONFIRM_HEADER = "X-DELETE-CONFIRMED";

    private final SuperadminDeleteGuardService guardService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"DELETE".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri == null || !uri.matches(".*/api/customers/\\d+$");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String confirmed = request.getHeader(CONFIRM_HEADER);
        String rawPassword = request.getHeader(PASSWORD_HEADER);

        if (!StringUtils.hasText(confirmed) || !StringUtils.hasText(rawPassword)) {
            writeForbidden(response, "Masukan password superadmin terlebih dahulu.");
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!guardService.isPasswordValid(rawPassword, auth)) {
            writeForbidden(response, "Password superadmin tidak sesuai.");
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
