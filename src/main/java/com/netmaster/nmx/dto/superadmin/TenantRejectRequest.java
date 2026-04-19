package com.netmaster.nmx.dto.superadmin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantRejectRequest {

    @NotBlank
    @Size(max = 500)
    private String reason;
}
