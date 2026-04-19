package com.netmaster.nmx.dto.superadmin;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class TenantInspectResponse {
    private Long tenantId;
    private Long registrationId;
    private String companyName;
    private String slug;
    private String email;
    private String phone;
    private String address;
    private String status;
    private String subscriptionPlanCode;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;

    private String ownerName;
    private String ownerEmail;
    private String ownerUsername;
    private String requestedPlanCode;
    private String registrationStatus;
}

