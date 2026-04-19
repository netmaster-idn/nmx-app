package com.netmaster.nmx.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BankAccountRequest {

    @NotBlank
    private String bankName;

    @NotBlank
    private String accountName;

    @NotBlank
    private String accountNumber;

    private String instructions;
    private Boolean primary;
}
