package com.netmaster.nmx.dto.superadmin;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SuperAdminLoginRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
