package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDocumentItemView {

    private String description;
    private BigDecimal rate;
    private BigDecimal quantity;
    private String unitName;
    private BigDecimal subtotal;
    private String rateLabel;
    private String subtotalLabel;
}
