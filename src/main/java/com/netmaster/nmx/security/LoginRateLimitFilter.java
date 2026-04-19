package com.netmaster.nmx.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_SECONDS = 60;
    private static final int MAX_ATTEMPTS = 10;
    private final Map<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !("/register-tenant".equals(path) || "/superadmin/login".equals(path) || "/tenant/login".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String key = request.getRemoteAddr() + ":" + request.getRequestURI();
        AttemptWindow window = attempts.computeIfAbsent(key, ignored -> new AttemptWindow());
        if (!window.allow()) {
            response.sendError(429, "Too many attempts. Please retry later.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static final class AttemptWindow {
        private long windowStartEpoch = Instant.now().getEpochSecond();
        private int count = 0;

        synchronized boolean allow() {
            long now = Instant.now().getEpochSecond();
            if ((now - windowStartEpoch) >= WINDOW_SECONDS) {
                windowStartEpoch = now;
                count = 0;
            }
            count++;
            return count <= MAX_ATTEMPTS;
        }
    }
}
