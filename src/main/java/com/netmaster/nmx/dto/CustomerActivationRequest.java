package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerActivationRequest {
    private BigDecimal installationFee;
    private String paymentMethod;
    private LocalDate activationDate;
}
