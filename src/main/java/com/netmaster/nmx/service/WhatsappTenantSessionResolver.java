package com.netmaster.nmx.service;

import com.netmaster.nmx.config.TenantContextHolder;
import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.security.SessionAttributeKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class WhatsappTenantSessionResolver {

    private static final String DEFAULT_SESSION_ID = "default";
    private static final String TENANT_SESSION_PREFIX = "tenant-";

    public String resolveSessionId() {
        Long tenantId = resolveTenantIdFromRequest();
        if (tenantId == null) {
            tenantId = resolveTenantIdFromTenantContext();
        }
        if (tenantId == null) {
            return DEFAULT_SESSION_ID;
        }
        return TENANT_SESSION_PREFIX + tenantId;
    }

    private Long resolveTenantIdFromRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }

        HttpServletRequest request = servletAttributes.getRequest();
        Object resolvedTenant = request.getAttribute("resolvedTenant");
        if (resolvedTenant instanceof Tenant tenant && tenant.getId() != null) {
            return tenant.getId();
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        Long supportTenantId = asLong(session.getAttribute(SessionAttributeKeys.SUPPORT_TENANT_ID));
        if (supportTenantId != null) {
            return supportTenantId;
        }

        return asLong(session.getAttribute(SessionAttributeKeys.TENANT_ID));
    }

    private Long resolveTenantIdFromTenantContext() {
        String tenantKey = TenantContextHolder.getTenantKey();
        if (tenantKey == null || !tenantKey.startsWith(TENANT_SESSION_PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(tenantKey.substring(TENANT_SESSION_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
