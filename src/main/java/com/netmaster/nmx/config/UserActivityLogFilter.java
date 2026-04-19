package com.netmaster.nmx.config;

import com.netmaster.nmx.service.AppLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UserActivityLogFilter extends OncePerRequestFilter {

    private static final Set<String> STATIC_PREFIXES = Set.of(
            "/css/",
            "/js/",
            "/images/",
            "/uploads/",
            "/favicon",
            "/error"
    );

    private final AppLogService appLogService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            return true;
        }

        if (STATIC_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }

        return "/login".equals(path) || "/logout".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            appLogService.recordError(request, authentication, ex, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unhandled request failure");
            throw ex;
        } finally {
            if (shouldCaptureActivity(request)) {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                appLogService.recordActivity(
                        request,
                        authentication,
                        response.getStatus(),
                        System.currentTimeMillis() - startedAt
                );
            }
        }
    }

    private boolean shouldCaptureActivity(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        boolean mutation = !"GET".equalsIgnoreCase(method)
                && !"HEAD".equalsIgnoreCase(method)
                && !"OPTIONS".equalsIgnoreCase(method)
                && !"TRACE".equalsIgnoreCase(method);
        boolean pageView = "GET".equalsIgnoreCase(method) && path != null && !path.startsWith("/api/");
        return mutation || pageView;
    }
}
