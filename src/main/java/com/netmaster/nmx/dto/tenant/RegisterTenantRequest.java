package com.netmaster.nmx.dto.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterTenantRequest {

    @NotBlank
    @Size(min = 3, max = 150)
    private String companyName;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Pattern(regexp = "^[+]?[0-9]{9,15}$")
    private String phone;

    @Size(max = 500)
    private String address;

    @NotBlank
    @Size(min = 3, max = 150)
    private String ownerName;

    @NotBlank
    @Email
    private String ownerEmail;

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9._-]{4,100}$")
    private String ownerUsername;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @NotBlank
    @Size(min = 8, max = 100)
    private String confirmPassword;

    @Size(max = 50)
    private String requestedPlanCode;
}
