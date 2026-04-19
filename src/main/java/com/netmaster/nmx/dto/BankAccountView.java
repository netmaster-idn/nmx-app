package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BankAccountView {
    private Long id;
    private String bankName;
    private String accountName;
    private String accountNumber;
    private String instructions;
    private boolean primary;
}
