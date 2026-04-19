package com.netmaster.nmx.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

public final class TenantRouteAccessPolicy {

    private static final Set<String> STATIC_PREFIXES = Set.of(
            "/css/",
            "/js/",
            "/images/",
            "/img/",
            "/uploads/",
            "/webjars/",
            "/favicon",
            "/actuator/"
    );

    private static final Set<String> TENANT_SCOPED_PREFIXES = Set.of(
            "/tenant/",
            "/dashboard",
            "/pelanggan",
            "/monitoring",
            "/reports",
            "/automation",
            "/system",
            "/crm",
            "/mapping",
            "/company",
            "/setting",
            "/user",
            "/network",
            "/finance",
            "/ticketing",
            "/api/"
    );

    private TenantRouteAccessPolicy() {
    }

    public static boolean isPublicPath(String path) {
        return "/".equals(path)
                || "/login".equals(path)
                || "/logout".equals(path)
                || "/error".equals(path)
                || "/register-tenant".equals(path)
                || "/tenant/login".equals(path)
                || "/superadmin/login".equals(path);
    }

    public static boolean isStaticAsset(String path) {
        return path != null && STATIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    public static boolean isPlatformPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        return path.startsWith("/superadmin")
                || path.startsWith("/system/tenant-approval")
                || path.startsWith("/system/tenant-directory")
                || path.startsWith("/role/");
    }

    public static boolean requiresTenantContext(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        if (isPublicPath(path) || isStaticAsset(path) || isPlatformPath(path)) {
            return false;
        }
        return TENANT_SCOPED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    public static boolean isMutatingRequest(HttpServletRequest request) {
        String method = request.getMethod();
        return !"GET".equalsIgnoreCase(method)
                && !"HEAD".equalsIgnoreCase(method)
                && !"OPTIONS".equalsIgnoreCase(method)
                && !"TRACE".equalsIgnoreCase(method);
    }
}
