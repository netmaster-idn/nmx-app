package com.netmaster.nmx.dto.superadmin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantUpdateRequest {

    @Size(min = 3, max = 150)
    private String companyName;

    @Email
    private String email;

    @Size(max = 30)
    private String phone;

    @Size(max = 500)
    private String address;

    private String status;

    private String subscriptionPlanCode;
}
