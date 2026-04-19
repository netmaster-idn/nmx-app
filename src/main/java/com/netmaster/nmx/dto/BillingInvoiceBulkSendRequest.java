package com.netmaster.nmx.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BillingInvoiceBulkSendRequest(
        @NotEmpty List<Long> invoiceIds,
        Boolean force
) {
}
