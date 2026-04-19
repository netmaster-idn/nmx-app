package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDataRowDTO {
    private Long id;
    private String customerCode;
    private String fullName;
    private String phone;
    private String email;
    private String ktpNumber;
    private String installationAddress;
    private String status;
    private LocalDate registrationDate;
    private Long serviceId;
    private String pppoeUsername;
    private String packageName;
    private String ipAddress;
    private BigDecimal monthlyFee;
    private BigDecimal installationFee;
    private String paymentStatus;
}
