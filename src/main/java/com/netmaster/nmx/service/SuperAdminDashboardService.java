package com.netmaster.nmx.service;

import com.netmaster.nmx.dto.superadmin.SuperAdminDashboardResponse;
import com.netmaster.nmx.master.model.SuperadminActivityLog;
import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.model.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SuperAdminDashboardService {

    private final TenantService tenantService;

    public SuperAdminDashboardResponse buildDashboard() {
        List<Tenant> tenants = tenantService.getAllTenants();
        List<Tenant> pendingTenants = tenantService.getPendingTenants();
        List<SuperadminActivityLog> activityLogs = tenantService.getRecentActivityLogs();

        long activeTenants = tenants.stream().filter(tenant -> tenant.getStatus() == TenantStatus.ACTIVE).count();
        long suspendedTenants = tenants.stream().filter(tenant -> tenant.getStatus() == TenantStatus.SUSPENDED).count();

        return SuperAdminDashboardResponse.builder()
                .activeTenants(activeTenants)
                .pendingTenants(pendingTenants.size())
                .suspendedTenants(suspendedTenants)
                .provisionedDatabases(tenantService.countProvisionedDatabases())
                .pendingApprovals(pendingTenants.size())
                .failedProvisioning(tenantService.countFailedProvisioning())
                .criticalSuspendedTenants(suspendedTenants)
                .pendingQueue(pendingTenants.stream().map(this::toTenantRow).toList())
                .tenants(tenants.stream().map(this::toTenantRow).toList())
                .recentActivity(activityLogs.stream().map(this::toActivityRow).toList())
                .build();
    }

    private SuperAdminDashboardResponse.TenantRow toTenantRow(Tenant tenant) {
        TenantStatus status = tenant.getStatus();
        return SuperAdminDashboardResponse.TenantRow.builder()
                .id(tenant.getId())
                .companyName(tenant.getCompanyName())
                .status(status.name())
                .databaseName(tenant.getDbName())
                .createdAt(tenant.getCreatedAt())
                .slug(tenant.getSlug())
                .canApprove(status == TenantStatus.PENDING)
                .canReject(status == TenantStatus.PENDING)
                .canSuspend(status == TenantStatus.ACTIVE)
                .canActivate(status == TenantStatus.SUSPENDED)
                .build();
    }

    private SuperAdminDashboardResponse.ActivityRow toActivityRow(SuperadminActivityLog activityLog) {
        return SuperAdminDashboardResponse.ActivityRow.builder()
                .tenantId(activityLog.getTenantId())
                .activity(activityLog.getActivity())
                .createdAt(activityLog.getCreatedAt())
                .build();
    }
}
