package com.netmaster.nmx.dto.superadmin;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantApprovalRequest {

    @Size(max = 50)
    private String subscriptionPlanCode;

    @Size(max = 500)
    private String notes;
}
