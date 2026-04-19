package com.netmaster.nmx.dto.superadmin;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class SuperAdminDashboardResponse {
    private long activeTenants;
    private long pendingTenants;
    private long suspendedTenants;
    private long provisionedDatabases;
    private long pendingApprovals;
    private long failedProvisioning;
    private long criticalSuspendedTenants;
    private List<TenantRow> pendingQueue;
    private List<TenantRow> tenants;
    private List<ActivityRow> recentActivity;

    @Getter
    @Builder
    public static class TenantRow {
        private Long id;
        private String companyName;
        private String status;
        private String databaseName;
        private LocalDateTime createdAt;
        private String slug;
        private boolean canApprove;
        private boolean canReject;
        private boolean canSuspend;
        private boolean canActivate;
    }

    @Getter
    @Builder
    public static class ActivityRow {
        private Long tenantId;
        private String activity;
        private LocalDateTime createdAt;
    }
}
