package com.netmaster.nmx.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecordPaymentRequest {

    @NotNull(message = "Jumlah pembayaran wajib diisi")
    @DecimalMin(value = "0.01", message = "Jumlah pembayaran harus lebih besar dari 0")
    private BigDecimal amount;

    private LocalDate paymentDate;
    private String paymentMethod;
    private String referenceNumber;
    private String notes;
}
