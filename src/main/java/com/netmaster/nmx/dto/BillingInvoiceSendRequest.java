package com.netmaster.nmx.dto;

import jakarta.validation.constraints.NotNull;

public record BillingInvoiceSendRequest(
        @NotNull Long customerId,
        @NotNull Long invoiceId,
        Boolean force
) {
}
