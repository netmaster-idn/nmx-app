package com.netmaster.nmx.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDTO {
    
    private Long id;
    private Long customerServiceId;
    private String invoiceNumber;

    @NotNull(message = "Customer wajib dipilih")
    private Long customerId;
    private String customerName;
    private String customerCode;
    private String pppoeUsername;
    private String packageName;

    @DecimalMin(value = "0.0", inclusive = true, message = "Biaya bulanan tidak boleh negatif")
    private BigDecimal monthlyFee;

    @DecimalMin(value = "0.0", inclusive = true, message = "Biaya instalasi tidak boleh negatif")
    private BigDecimal installationFee;

    @DecimalMin(value = "0.0", inclusive = true, message = "Biaya tambahan tidak boleh negatif")
    private BigDecimal otherCharges;
    private BigDecimal totalAmount;
    private LocalDate billingMonth;
    private LocalDate dueDate;
    private String status; // pending, paid, overdue, cancelled
    private LocalDate paymentDate;
    private String paymentMethod;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

