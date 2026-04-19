package com.netmaster.nmx.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuickPayInvoiceRequest {
    private BigDecimal amount;
    private LocalDate paymentDate;

    @NotBlank(message = "Metode pembayaran wajib diisi")
    private String paymentMethod;

    private String notes;
}
