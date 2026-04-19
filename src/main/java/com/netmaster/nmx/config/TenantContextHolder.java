package com.netmaster.nmx.config;

public final class TenantContextHolder {

    private static final ThreadLocal<String> TENANT_KEY = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void setTenantKey(String tenantKey) {
        TENANT_KEY.set(tenantKey);
    }

    public static String getTenantKey() {
        return TENANT_KEY.get();
    }

    public static void clear() {
        TENANT_KEY.remove();
    }
}
