package com.netmaster.nmx.dto.superadmin;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TenantSummaryResponse {
    private Long tenantId;
    private String tenantSlug;
    private long totalCustomers;
    private long totalInvoices;
    private long totalTickets;
    private long totalDevices;
    private long totalProjects;
    private String subscriptionPlan;
    private String tenantStatus;
}
