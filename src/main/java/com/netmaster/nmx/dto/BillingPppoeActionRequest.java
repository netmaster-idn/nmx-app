package com.netmaster.nmx.dto;

import jakarta.validation.constraints.NotBlank;

public record BillingPppoeActionRequest(
        @NotBlank String reason
) {
}
