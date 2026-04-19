package com.netmaster.nmx.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenantRoleAccessTest {

    @Test
    void permissionLevel_shouldTreatTenantSuperAdminAsFullAccess() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "tenant-owner",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_SUPER_ADMIN"))
        );

        assertThat(TenantRoleAccess.permissionLevel(authentication)).isEqualTo("FULL");
        assertThat(TenantRoleAccess.canManageUsers(authentication)).isTrue();
        assertThat(TenantRoleAccess.canDelete(authentication)).isTrue();
    }

    @Test
    void permissionLevel_shouldTreatLegacyTenantAdminAsFullAccess() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "legacy-tenant-owner",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_TENANT_ADMIN"))
        );

        assertThat(TenantRoleAccess.permissionLevel(authentication)).isEqualTo("FULL");
        assertThat(TenantRoleAccess.canManageUsers(authentication)).isTrue();
        assertThat(TenantRoleAccess.canDelete(authentication)).isTrue();
    }
}
