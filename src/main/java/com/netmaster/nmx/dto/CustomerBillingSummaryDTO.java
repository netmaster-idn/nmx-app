package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerBillingSummaryDTO {
    private Long customerId;
    private BigDecimal totalPaid;
    private BigDecimal totalUnpaid;
    private LocalDate lastPaymentDate;
}
