package com.netmaster.nmx.dto.superadmin;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class TenantDirectoryResponse {
    private Long id;
    private String companyName;
    private String slug;
    private String email;
    private String phone;
    private String status;
    private String subscriptionPlan;
    private LocalDate subscriptionEndsAt;
    private LocalDateTime approvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}
