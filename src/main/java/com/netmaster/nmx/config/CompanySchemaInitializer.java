package com.netmaster.nmx.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "nmx.runtime.schema-sync.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class CompanySchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        apply("ALTER TABLE IF EXISTS company_profiles ADD COLUMN IF NOT EXISTS province_code VARCHAR(20)");
        apply("ALTER TABLE IF EXISTS company_profiles ADD COLUMN IF NOT EXISTS province_name VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS company_profiles ADD COLUMN IF NOT EXISTS regency_code VARCHAR(20)");
        apply("ALTER TABLE IF EXISTS company_profiles ADD COLUMN IF NOT EXISTS regency_name VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS company_profiles ADD COLUMN IF NOT EXISTS district_code VARCHAR(20)");
        apply("ALTER TABLE IF EXISTS company_profiles ADD COLUMN IF NOT EXISTS district_name VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS company_profiles ADD COLUMN IF NOT EXISTS village_code VARCHAR(20)");
        apply("ALTER TABLE IF EXISTS company_profiles ADD COLUMN IF NOT EXISTS village_name VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS company_profiles ADD COLUMN IF NOT EXISTS rt VARCHAR(10)");
        apply("ALTER TABLE IF EXISTS company_profiles ADD COLUMN IF NOT EXISTS rw VARCHAR(10)");
        apply("ALTER TABLE IF EXISTS company_profiles ADD COLUMN IF NOT EXISTS building_number VARCHAR(50)");
        apply("ALTER TABLE IF EXISTS company_profiles ADD COLUMN IF NOT EXISTS street_name VARCHAR(200)");
        apply("ALTER TABLE IF EXISTS company_profiles ADD COLUMN IF NOT EXISTS google_maps_coordinates VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS company_profiles ADD COLUMN IF NOT EXISTS support_email VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS whatsapp_reminder_enabled BOOLEAN NOT NULL DEFAULT FALSE");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS whatsapp_reminder_lead_days INT DEFAULT 3");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS whatsapp_hourly_limit INT DEFAULT 6");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS whatsapp_batch_interval_minutes INT DEFAULT 10");
        apply("ALTER TABLE IF EXISTS company_settings ADD COLUMN IF NOT EXISTS whatsapp_send_start_hour INT DEFAULT 1");
        apply("ALTER TABLE IF EXISTS whatsapp_reminder_dispatch_logs ADD COLUMN IF NOT EXISTS document_type VARCHAR(30)");
        apply("ALTER TABLE IF EXISTS whatsapp_reminder_dispatch_logs ADD COLUMN IF NOT EXISTS message_id VARCHAR(160)");
        apply("ALTER TABLE IF EXISTS whatsapp_reminder_dispatch_logs ADD COLUMN IF NOT EXISTS delivery_status VARCHAR(30)");
        apply("ALTER TABLE IF EXISTS whatsapp_reminder_dispatch_logs ADD COLUMN IF NOT EXISTS gateway_message TEXT");
        apply("ALTER TABLE IF EXISTS whatsapp_reminder_dispatch_logs ADD COLUMN IF NOT EXISTS read_at TIMESTAMP");
        apply("""
                CREATE TABLE IF NOT EXISTS whatsapp_reminder_dispatch_logs (
                    id BIGSERIAL PRIMARY KEY,
                    invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
                    phone_number VARCHAR(30),
                    lead_days INT NOT NULL DEFAULT 3,
                    scheduled_for_date DATE NOT NULL,
                    dispatch_status VARCHAR(20) NOT NULL DEFAULT 'pending',
                    document_type VARCHAR(30),
                    message_id VARCHAR(160),
                    delivery_status VARCHAR(30),
                    gateway_message TEXT,
                    sent_at TIMESTAMP,
                    read_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        apply("CREATE INDEX IF NOT EXISTS idx_wa_dispatch_logs_invoice ON whatsapp_reminder_dispatch_logs(invoice_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_wa_dispatch_logs_sent_at ON whatsapp_reminder_dispatch_logs(sent_at)");
        apply("CREATE INDEX IF NOT EXISTS idx_wa_dispatch_logs_status ON whatsapp_reminder_dispatch_logs(dispatch_status)");
        apply("CREATE INDEX IF NOT EXISTS idx_wa_dispatch_logs_message_id ON whatsapp_reminder_dispatch_logs(message_id)");
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("Skipping company schema sync for SQL [{}]: {}", sql, ex.getMessage());
        }
    }
}
