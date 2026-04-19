package com.netmaster.nmx.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BillingAutomationSettingsRequest(
        @NotNull Boolean autoSendInvoice,
        @NotNull @Min(0) @Max(30) Integer invoiceSendDaysBeforeDue,
        @NotNull Boolean autoSendReceipt,
        @NotNull Boolean autoDisablePppoe,
        @NotNull @Min(0) @Max(90) Integer latePaymentDisableDays,
        @NotNull String disableMode,
        @NotNull Boolean sendWarningBeforeDisable,
        String warningTemplate,
        @NotNull Boolean autoEnablePppoeAfterPayment,
        @NotNull String executionTime,
        @NotNull Boolean isActive
) {
}
