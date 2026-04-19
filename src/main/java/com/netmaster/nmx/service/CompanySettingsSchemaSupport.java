package com.netmaster.nmx.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanySettingsSchemaSupport {

    private final JdbcTemplate jdbcTemplate;

    public void ensureCompanySettingsSchema() {
        apply("""
                CREATE TABLE IF NOT EXISTS company_settings (
                    id BIGSERIAL PRIMARY KEY,
                    company_profile_id BIGINT,
                    default_currency_code VARCHAR(10) DEFAULT 'IDR',
                    default_locale_code VARCHAR(20) DEFAULT 'id-ID',
                    default_tax_rate DECIMAL(5,2) DEFAULT 0,
                    default_invoice_title VARCHAR(100) DEFAULT 'INVOICE',
                    default_invoice_subtitle VARCHAR(200) DEFAULT 'Document Payment Information',
                    default_footer_note TEXT DEFAULT 'Data Belum di Set',
                    whatsapp_reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE,
                    whatsapp_reminder_lead_days INT DEFAULT 3,
                    whatsapp_hourly_limit INT DEFAULT 6,
                    whatsapp_batch_interval_minutes INT DEFAULT 10,
                    whatsapp_send_start_hour INT DEFAULT 1,
                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS company_profile_id BIGINT");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS default_currency_code VARCHAR(10) DEFAULT 'IDR'");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS default_locale_code VARCHAR(20) DEFAULT 'id-ID'");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS default_tax_rate DECIMAL(5,2) DEFAULT 0");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS default_invoice_title VARCHAR(100) DEFAULT 'INVOICE'");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS default_invoice_subtitle VARCHAR(200) DEFAULT 'Document Payment Information'");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS default_footer_note TEXT DEFAULT 'Data Belum di Set'");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS whatsapp_reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS whatsapp_reminder_lead_days INT DEFAULT 3");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS whatsapp_hourly_limit INT DEFAULT 6");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS whatsapp_batch_interval_minutes INT DEFAULT 10");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS whatsapp_send_start_hour INT DEFAULT 1");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
        apply("""
                INSERT INTO company_settings (
                    default_currency_code,
                    default_locale_code,
                    default_tax_rate,
                    default_invoice_title,
                    default_invoice_subtitle,
                    default_footer_note,
                    whatsapp_reminder_enabled,
                    whatsapp_reminder_lead_days,
                    whatsapp_hourly_limit,
                    whatsapp_batch_interval_minutes,
                    whatsapp_send_start_hour,
                    is_active
                )
                SELECT 'IDR', 'id-ID', 0, 'INVOICE', 'Document Payment Information',
                       'Data Belum di Set', FALSE, 3, 6, 10, 1, TRUE
                WHERE NOT EXISTS (SELECT 1 FROM company_settings)
                """);
    }

    public boolean isMissingRelation(Throwable throwable, String relation) {
        if (throwable == null || relation == null || relation.isBlank()) {
            return false;
        }
        String normalizedRelation = relation.toLowerCase();
        Throwable cursor = throwable;
        while (cursor != null) {
            String message = cursor.getMessage();
            if (message != null) {
                String normalizedMessage = message.toLowerCase();
                if ((normalizedMessage.contains("relation \"" + normalizedRelation + "\"")
                        || normalizedMessage.contains("relation '" + normalizedRelation + "'")
                        || normalizedMessage.contains("relation " + normalizedRelation))
                        && normalizedMessage.contains("does not exist")) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("Skipping company_settings schema sync for SQL [{}]: {}", sql, ex.getMessage());
        }
    }
}
