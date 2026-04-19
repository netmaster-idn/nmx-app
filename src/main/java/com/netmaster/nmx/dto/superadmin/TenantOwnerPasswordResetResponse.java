package com.netmaster.nmx.dto.superadmin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TenantOwnerPasswordResetResponse {
    private Long tenantId;
    private String ownerUsername;
    private String temporaryPassword;
}

