package com.netmaster.nmx.dto.tenant;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TenantDashboardResponse {
    private String tenantSlug;
    private String tenantName;
    private String tenantStatus;
    private long totalCustomers;
    private long totalInvoices;
    private long totalTickets;
    private long totalDevices;
}
