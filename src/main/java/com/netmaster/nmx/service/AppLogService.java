package com.netmaster.nmx.service;

import com.netmaster.nmx.config.TenantContextHolder;
import com.netmaster.nmx.dto.ActivityLogRowDTO;
import com.netmaster.nmx.dto.ErrorLogRowDTO;
import com.netmaster.nmx.model.AppActivityLog;
import com.netmaster.nmx.model.AppErrorLog;
import com.netmaster.nmx.model.User;
import com.netmaster.nmx.repository.AppActivityLogRepository;
import com.netmaster.nmx.repository.AppErrorLogRepository;
import com.netmaster.nmx.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppLogService {

    private final AppActivityLogRepository activityLogRepository;
    private final AppErrorLogRepository errorLogRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Map<String, Boolean> logSchemaReadyByTenant = new ConcurrentHashMap<>();
    private final Map<String, Object> schemaLocks = new ConcurrentHashMap<>();

    public List<ActivityLogRowDTO> getRecentActivityRows(int limit) {
        try {
            ensureLogSchema();
            return activityLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, sanitizeLimit(limit))).stream()
                    .map(this::toActivityRow)
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to read activity logs for tenant [{}]: {}", resolveSchemaKey(), ex.getMessage());
            return List.of();
        }
    }

    public List<ErrorLogRowDTO> getRecentErrorRows(int limit) {
        try {
            ensureLogSchema();
            return errorLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, sanitizeLimit(limit))).stream()
                    .map(this::toErrorRow)
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to read error logs for tenant [{}]: {}", resolveSchemaKey(), ex.getMessage());
            return List.of();
        }
    }

    public void recordActivity(HttpServletRequest request, Authentication authentication, int statusCode, long durationMs) {
        try {
            ensureLogSchema();
            if (!isAuthenticated(authentication)) {
                return;
            }

            User user = resolveUser(authentication);
            AppActivityLog logEntry = new AppActivityLog();
            logEntry.setUsername(limitText(resolveUsername(authentication, user), 100));
            logEntry.setFullName(limitText(user != null ? safe(user.getFullName()) : null, 150));
            logEntry.setRoleNames(limitText(resolveRoles(authentication), 250));
            logEntry.setModuleName(limitText(resolveModuleName(request.getRequestURI()), 80));
            logEntry.setActionType(limitText(resolveActionType(request), 40));
            logEntry.setActionLabel(limitText(resolveActionLabel(request), 180));
            logEntry.setHttpMethod(limitText(request.getMethod(), 12));
            logEntry.setRequestPath(limitText(buildPathWithQuery(request), 255));
            logEntry.setStatusCode(statusCode);
            logEntry.setStatus(resolveStatus(statusCode));
            logEntry.setIpAddress(limitText(resolveClientIp(request), 80));
            logEntry.setDurationMs(durationMs);
            activityLogRepository.save(logEntry);
        } catch (Exception ex) {
            log.warn("Failed to save activity log: {}", ex.getMessage());
        }
    }

    public void recordError(HttpServletRequest request, Authentication authentication, Throwable throwable, int statusCode, String actionLabel) {
        try {
            ensureLogSchema();
            User user = resolveUser(authentication);
            Throwable rootCause = resolveRootCause(throwable);

            AppErrorLog errorLog = new AppErrorLog();
            errorLog.setUsername(limitText(resolveUsername(authentication, user), 100));
            errorLog.setFullName(limitText(user != null ? safe(user.getFullName()) : null, 150));
            errorLog.setRoleNames(limitText(resolveRoles(authentication), 250));
            errorLog.setModuleName(limitText(resolveModuleName(request != null ? request.getRequestURI() : null), 80));
            errorLog.setActionLabel(limitText(safe(actionLabel), 180));
            errorLog.setHttpMethod(request != null ? limitText(request.getMethod(), 12) : null);
            errorLog.setRequestPath(request != null ? limitText(buildPathWithQuery(request), 255) : null);
            errorLog.setStatusCode(statusCode);
            errorLog.setErrorType(limitText(throwable != null ? throwable.getClass().getSimpleName() : "UnknownError", 160));
            errorLog.setErrorMessage(limitText(throwable != null ? throwable.getMessage() : null, 1000));
            errorLog.setRootCauseMessage(limitText(rootCause != null ? rootCause.getMessage() : null, 1000));
            errorLog.setStackTrace(limitText(toStackTrace(throwable), 12000));
            errorLog.setIpAddress(request != null ? limitText(resolveClientIp(request), 80) : null);
            errorLogRepository.save(errorLog);
        } catch (Exception ex) {
            log.warn("Failed to save error log: {}", ex.getMessage());
        }
    }

    public String resolveModuleName(String path) {
        if (path == null || path.isBlank()) {
            return "Umum";
        }

        String[] segments = Arrays.stream(path.split("/"))
                .filter(segment -> segment != null && !segment.isBlank())
                .toArray(String[]::new);

        if (segments.length == 0) {
            return "Dashboard";
        }

        if ("api".equalsIgnoreCase(segments[0])) {
            return segments.length > 1 ? beautifySegment(segments[1]) : "API";
        }

        return beautifySegment(segments[0]);
    }

    private ActivityLogRowDTO toActivityRow(AppActivityLog entity) {
        return new ActivityLogRowDTO(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getUsername(),
                entity.getFullName(),
                entity.getRoleNames(),
                entity.getModuleName(),
                entity.getActionType(),
                entity.getActionLabel(),
                entity.getHttpMethod(),
                entity.getRequestPath(),
                entity.getStatusCode(),
                entity.getStatus(),
                entity.getIpAddress(),
                entity.getDurationMs()
        );
    }

    private ErrorLogRowDTO toErrorRow(AppErrorLog entity) {
        return new ErrorLogRowDTO(
                entity.getId(),
                entity.getCreatedAt(),
                entity.getUsername(),
                entity.getFullName(),
                entity.getRoleNames(),
                entity.getModuleName(),
                entity.getActionLabel(),
                entity.getHttpMethod(),
                entity.getRequestPath(),
                entity.getStatusCode(),
                entity.getErrorType(),
                entity.getErrorMessage(),
                entity.getRootCauseMessage(),
                entity.getStackTrace(),
                entity.getIpAddress()
        );
    }

    private int sanitizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private User resolveUser(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return null;
        }

        try {
            return userRepository.findByUsername(authentication.getName()).orElse(null);
        } catch (Exception ex) {
            log.debug("Unable to resolve user {} for logging: {}", authentication.getName(), ex.getMessage());
            return null;
        }
    }

    private String resolveUsername(Authentication authentication, User user) {
        if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        if (isAuthenticated(authentication)) {
            return authentication.getName();
        }
        return "anonymous";
    }

    private String resolveRoles(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return "ANONYMOUS";
        }

        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    private String resolveActionType(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method)) {
            return request.getRequestURI().startsWith("/api/") ? "API_READ" : "PAGE_VIEW";
        }
        return "USER_ACTION";
    }

    private String resolveActionLabel(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String module = resolveModuleName(request.getRequestURI());
        if ("GET".equals(method) && !request.getRequestURI().startsWith("/api/")) {
            return "Membuka halaman " + module;
        }

        String verb = switch (method) {
            case "POST" -> "Menjalankan";
            case "PUT" -> "Memperbarui";
            case "PATCH" -> "Mengubah";
            case "DELETE" -> "Menghapus";
            default -> "Mengakses";
        };
        return verb + " request " + method + " " + request.getRequestURI();
    }

    private String resolveStatus(int statusCode) {
        if (statusCode >= 500) {
            return "error";
        }
        if (statusCode >= 400) {
            return "warning";
        }
        return "success";
    }

    private String buildPathWithQuery(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return uri;
        }
        return uri + "?" + query;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Throwable resolveRootCause(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String toStackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }

        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private String beautifySegment(String value) {
        if (value == null || value.isBlank()) {
            return "Umum";
        }

        String normalized = value
                .replace('-', ' ')
                .replace('_', ' ')
                .trim();

        String[] words = normalized.split("\\s+");
        return Arrays.stream(words)
                .filter(word -> !word.isBlank())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String limitText(String value, int maxLength) {
        String safeValue = safe(value);
        if (safeValue == null || safeValue.length() <= maxLength) {
            return safeValue;
        }
        return safeValue.substring(0, maxLength);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void ensureLogSchema() {
        String schemaKey = resolveSchemaKey();
        if (Boolean.TRUE.equals(logSchemaReadyByTenant.get(schemaKey))) {
            return;
        }
        Object lock = schemaLocks.computeIfAbsent(schemaKey, ignored -> new Object());
        synchronized (lock) {
            if (Boolean.TRUE.equals(logSchemaReadyByTenant.get(schemaKey))) {
                return;
            }
            applySchema("""
                    CREATE TABLE IF NOT EXISTS app_activity_logs (
                        id BIGSERIAL PRIMARY KEY,
                        username VARCHAR(100),
                        full_name VARCHAR(150),
                        role_names VARCHAR(250),
                        module_name VARCHAR(80),
                        action_type VARCHAR(40),
                        action_label VARCHAR(180),
                        http_method VARCHAR(12),
                        request_path VARCHAR(255),
                        status_code INTEGER,
                        status VARCHAR(20),
                        ip_address VARCHAR(80),
                        duration_ms BIGINT,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                    )
                    """);
            applySchema("""
                    CREATE TABLE IF NOT EXISTS app_error_logs (
                        id BIGSERIAL PRIMARY KEY,
                        username VARCHAR(100),
                        full_name VARCHAR(150),
                        role_names VARCHAR(250),
                        module_name VARCHAR(80),
                        action_label VARCHAR(180),
                        http_method VARCHAR(12),
                        request_path VARCHAR(255),
                        status_code INTEGER,
                        error_type VARCHAR(160),
                        error_message VARCHAR(1000),
                        root_cause_message VARCHAR(1000),
                        stack_trace TEXT,
                        ip_address VARCHAR(80),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                    )
                    """);
            applySchema("CREATE INDEX IF NOT EXISTS idx_app_activity_logs_created_at ON app_activity_logs (created_at DESC)");
            applySchema("CREATE INDEX IF NOT EXISTS idx_app_activity_logs_username ON app_activity_logs (username)");
            applySchema("CREATE INDEX IF NOT EXISTS idx_app_error_logs_created_at ON app_error_logs (created_at DESC)");
            applySchema("CREATE INDEX IF NOT EXISTS idx_app_error_logs_username ON app_error_logs (username)");
            logSchemaReadyByTenant.put(schemaKey, true);
        }
    }

    private void applySchema(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            throw new IllegalStateException("Gagal menyiapkan schema app log: " + ex.getMessage(), ex);
        }
    }

    private String resolveSchemaKey() {
        String tenantKey = TenantContextHolder.getTenantKey();
        if (tenantKey == null || tenantKey.isBlank()) {
            return "__default__";
        }
        return tenantKey;
    }
}
