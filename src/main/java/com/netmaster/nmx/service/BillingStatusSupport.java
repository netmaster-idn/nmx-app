package com.netmaster.nmx.service;

import com.netmaster.nmx.model.Customer;
import com.netmaster.nmx.model.Invoice;
import com.netmaster.nmx.model.Payment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

@Service
public class BillingStatusSupport {

    public String normalizeInvoiceStatus(String rawStatus, LocalDate dueDate, BigDecimal totalAmount, BigDecimal amountPaid) {
        String normalized = normalize(rawStatus);
        BigDecimal total = totalAmount != null ? totalAmount : BigDecimal.ZERO;
        BigDecimal paid = amountPaid != null ? amountPaid : BigDecimal.ZERO;
        if ("paid".equals(normalized) || (total.signum() == 0 && paid.signum() == 0) || paid.compareTo(total) >= 0) {
            return "paid";
        }
        if ("no payment".equals(normalized) || "tidak bayar".equals(normalized)) {
            return "no_payment";
        }
        if ("failed".equals(normalized)) {
            return "failed";
        }
        if (dueDate != null && dueDate.isBefore(LocalDate.now())) {
            return "overdue";
        }
        if ("sent".equals(normalized)) {
            return "sent";
        }
        if ("scheduled".equals(normalized) || "draft".equals(normalized)) {
            return normalized;
        }
        if (paid.signum() > 0) {
            return "sent";
        }
        return "scheduled";
    }

    public String normalizePaymentStatus(Payment payment, Invoice invoice) {
        String normalized = normalize(payment != null ? payment.getStatus() : null);
        if ("failed".equals(normalized)) {
            return "failed";
        }
        if ("paid".equals(normalized)) {
            return "paid";
        }
        BigDecimal total = invoice != null && invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = invoice != null && invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
        if (paid.signum() > 0 && paid.compareTo(total) < 0) {
            return "partial";
        }
        if (paid.compareTo(total) >= 0 && total.signum() > 0) {
            return "paid";
        }
        return "pending";
    }

    public String normalizePppoeStatus(String rawStatus) {
        String normalized = normalize(rawStatus);
        return switch (normalized) {
            case "disabled", "suspended", "inactive" -> "disabled";
            case "enabled", "active", "online" -> "active";
            default -> "unknown";
        };
    }

    public String normalizeCustomerStatus(Customer customer) {
        String normalized = normalize(customer != null ? customer.getStatus() : null);
        return switch (normalized) {
            case "active" -> "active";
            case "suspended", "disabled" -> "suspended";
            default -> "inactive";
        };
    }

    public String normalizePhone(Customer customer) {
        String source = customer != null && StringUtils.hasText(customer.getWhatsappNumber())
                ? customer.getWhatsappNumber()
                : customer != null ? customer.getPhone() : null;
        if (!StringUtils.hasText(source)) {
            return null;
        }
        String digits = source.replaceAll("[^0-9]", "");
        if (digits.startsWith("0")) {
            digits = "62" + digits.substring(1);
        }
        if (!digits.startsWith("62")) {
            digits = "62" + digits;
        }
        return digits;
    }

    public String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT)
                .replace('_', ' ')
                .replace('-', ' ');
    }
}
