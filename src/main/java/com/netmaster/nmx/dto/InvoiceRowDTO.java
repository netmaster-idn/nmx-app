package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRowDTO {
    private Long id;
    private String invoiceNumber;
    private Long customerId;
    private String customerCode;
    private String customerName;
    private Long customerServiceId;
    private LocalDate billingMonth;
    private LocalDate dueDate;
    private BigDecimal monthlyFee;
    private BigDecimal installationFee;
    private BigDecimal otherCharges;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal outstandingAmount;
    private String status;
    private LocalDate paymentDate;
    private String paymentMethod;
    private String invoiceType;
    private String invoiceTypeLabel;
    private String paymentNotes;
    private String notes;
}
