package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistoryPaymentRowDTO {
    private Long id;
    private String customerCode;
    private String fullName;
    private String phone;
    private String packageName;
    private String status;
    private Long serviceId;
    private BigDecimal totalTagihan;
    private String paymentStatus;
}
