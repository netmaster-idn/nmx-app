package com.netmaster.nmx.service;

import java.math.BigDecimal;
import java.time.LocalDate;

record ActivationOptions(
        BigDecimal installationFee,
        String paymentMethod,
        LocalDate activationDate
) {
}
