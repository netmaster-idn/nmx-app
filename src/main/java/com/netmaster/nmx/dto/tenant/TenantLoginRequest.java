package com.netmaster.nmx.dto.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantLoginRequest {

    @Size(max = 100)
    private String tenantSlug;

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
