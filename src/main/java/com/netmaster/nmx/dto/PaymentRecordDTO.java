package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRecordDTO {
    private Long id;
    private Long invoiceId;
    private String invoiceNumber;
    private Long customerId;
    private String customerCode;
    private String customerName;
    private LocalDate dueDate;
    private BigDecimal invoiceAmount;
    private BigDecimal amountPaid;
    private BigDecimal outstandingAmount;
    private LocalDate paymentDate;
    private String paymentMethod;
    private String referenceNumber;
    private String status;
    private String invoiceType;
    private String invoiceTypeLabel;
    private String notes;
}
