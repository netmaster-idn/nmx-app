package com.netmaster.nmx.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

public final class TenantRoleAccess {

    private TenantRoleAccess() {
    }

    public static boolean canRead(Authentication authentication) {
        return authentication != null;
    }

    public static boolean canWrite(Authentication authentication) {
        return hasFullAccess(authentication) || hasAuthority(authentication, "ROLE_ADMIN");
    }

    public static boolean canDelete(Authentication authentication) {
        return hasFullAccess(authentication);
    }

    public static boolean canManageUsers(Authentication authentication) {
        return hasFullAccess(authentication);
    }

    public static String permissionLevel(Authentication authentication) {
        if (hasFullAccess(authentication)) {
            return "FULL";
        }
        if (hasAuthority(authentication, "ROLE_ADMIN")) {
            return "WRITE";
        }
        if (hasAuthority(authentication, "ROLE_SIDE_ADMIN")) {
            return "READ";
        }
        return canRead(authentication) ? "READ" : "NONE";
    }

    public static boolean hasFullAccess(Authentication authentication) {
        return hasAuthority(authentication, "ROLE_SUPER_ADMIN")
                || hasAuthority(authentication, "ROLE_TENANT_SUPER_ADMIN")
                || hasAuthority(authentication, "ROLE_TENANT_ADMIN");
    }

    public static boolean hasAuthority(Authentication authentication, String authorityName) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authorityName.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
