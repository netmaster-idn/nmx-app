package com.netmaster.nmx.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

@Component
public class InvoiceDocumentSupport {

    public static final String DEFAULT_TEXT = "Data Belum di Set";
    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("id-ID");

    public String safeValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : DEFAULT_TEXT;
    }

    public String safeLine(String... values) {
        if (values == null || values.length == 0) {
            return DEFAULT_TEXT;
        }
        return Arrays.stream(values)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .findFirst()
                .orElse(DEFAULT_TEXT);
    }

    public BigDecimal safeNumber(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    public LocalDate safeDate(LocalDate value, LocalDate fallback) {
        return value != null ? value : fallback;
    }

    public String formatDate(LocalDate value, String localeCode) {
        if (value == null) {
            return DEFAULT_TEXT;
        }
        Locale locale = resolveLocale(localeCode);
        return value.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale));
    }

    public String formatMoney(BigDecimal amount, String currencyCode, String localeCode) {
        Locale locale = resolveLocale(localeCode);
        Currency currency = resolveCurrency(currencyCode);
        NumberFormat formatter = NumberFormat.getCurrencyInstance(locale);
        formatter.setCurrency(currency);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(0);
        String formatted = formatter.format(safeNumber(amount));
        return "IDR".equalsIgnoreCase(currency.getCurrencyCode()) ? formatted.replace("Rp", "Rp.") : formatted;
    }

    public String formatRatePercent(BigDecimal rate) {
        BigDecimal normalized = safeNumber(rate).setScale(0, RoundingMode.HALF_UP);
        return normalized.toPlainString();
    }

    public String initials(String value) {
        if (!StringUtils.hasText(value)) {
            return "NM";
        }
        String[] parts = value.trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                builder.append(Character.toUpperCase(part.charAt(0)));
            }
            if (builder.length() == 2) {
                break;
            }
        }
        return builder.isEmpty() ? "NM" : builder.toString();
    }

    public boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    public String joinNonBlank(String delimiter, String... values) {
        if (values == null || values.length == 0) {
            return DEFAULT_TEXT;
        }
        String joined = Arrays.stream(values)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + delimiter + right)
                .orElse("");
        return StringUtils.hasText(joined) ? joined : DEFAULT_TEXT;
    }

    private Locale resolveLocale(String localeCode) {
        if (!StringUtils.hasText(localeCode)) {
            return DEFAULT_LOCALE;
        }
        Locale locale = Locale.forLanguageTag(localeCode);
        return locale == null || locale.getLanguage().isBlank() ? DEFAULT_LOCALE : locale;
    }

    private Currency resolveCurrency(String currencyCode) {
        try {
            return Currency.getInstance(StringUtils.hasText(currencyCode) ? currencyCode.trim().toUpperCase(Locale.ROOT) : "IDR");
        } catch (Exception ignored) {
            return Currency.getInstance("IDR");
        }
    }
}
