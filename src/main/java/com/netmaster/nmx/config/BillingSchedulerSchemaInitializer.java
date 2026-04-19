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
public class BillingSchedulerSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        apply("ALTER TABLE IF EXISTS customers ADD COLUMN IF NOT EXISTS whatsapp_number VARCHAR(30)");
        apply("ALTER TABLE IF EXISTS customers ADD COLUMN IF NOT EXISTS billing_due_day INT");
        apply("ALTER TABLE IF EXISTS customers ADD COLUMN IF NOT EXISTS pppoe_status VARCHAR(20) DEFAULT 'unknown'");
        apply("ALTER TABLE IF EXISTS customers ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE");
        apply("UPDATE customers SET whatsapp_number = COALESCE(NULLIF(TRIM(whatsapp_number), ''), NULLIF(TRIM(phone), '')) WHERE COALESCE(NULLIF(TRIM(whatsapp_number), ''), '') = ''");
        apply("UPDATE customers SET is_active = CASE WHEN LOWER(COALESCE(status, '')) IN ('active', 'pending') THEN TRUE ELSE FALSE END WHERE is_active IS NULL");
        apply("CREATE INDEX IF NOT EXISTS idx_customers_is_active ON customers(is_active)");
        apply("CREATE INDEX IF NOT EXISTS idx_customers_pppoe_status ON customers(pppoe_status)");

        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS sent_at TIMESTAMP");
        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS send_method VARCHAR(30)");
        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS whatsapp_status VARCHAR(30)");
        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS last_reminder_at TIMESTAMP");
        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP");
        apply("CREATE INDEX IF NOT EXISTS idx_invoices_sent_at ON invoices(sent_at)");
        apply("CREATE INDEX IF NOT EXISTS idx_invoices_whatsapp_status ON invoices(whatsapp_status)");

        apply("ALTER TABLE IF EXISTS payments ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'paid'");
        apply("ALTER TABLE IF EXISTS payments ADD COLUMN IF NOT EXISTS receipt_sent_at TIMESTAMP");
        apply("ALTER TABLE IF EXISTS payments ADD COLUMN IF NOT EXISTS receipt_send_method VARCHAR(30)");
        apply("UPDATE payments SET status = COALESCE(status, 'paid')");
        apply("CREATE INDEX IF NOT EXISTS idx_payments_status ON payments(status)");
        apply("CREATE INDEX IF NOT EXISTS idx_payments_receipt_sent_at ON payments(receipt_sent_at)");

        apply("""
                CREATE TABLE IF NOT EXISTS billing_automation_settings (
                    id BIGSERIAL PRIMARY KEY,
                    auto_send_invoice BOOLEAN NOT NULL DEFAULT FALSE,
                    invoice_send_days_before_due INT NOT NULL DEFAULT 7,
                    auto_send_receipt BOOLEAN NOT NULL DEFAULT FALSE,
                    auto_disable_pppoe BOOLEAN NOT NULL DEFAULT FALSE,
                    late_payment_disable_days INT NOT NULL DEFAULT 3,
                    disable_mode VARCHAR(20) NOT NULL DEFAULT 'automatic',
                    send_warning_before_disable BOOLEAN NOT NULL DEFAULT TRUE,
                    warning_template TEXT,
                    auto_enable_pppoe_after_payment BOOLEAN NOT NULL DEFAULT TRUE,
                    execution_time TIME NOT NULL DEFAULT '09:00:00',
                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                    updated_by VARCHAR(100),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        apply("""
                INSERT INTO billing_automation_settings (
                    auto_send_invoice,
                    invoice_send_days_before_due,
                    auto_send_receipt,
                    auto_disable_pppoe,
                    late_payment_disable_days,
                    disable_mode,
                    send_warning_before_disable,
                    warning_template,
                    auto_enable_pppoe_after_payment,
                    execution_time,
                    is_active
                )
                SELECT FALSE, 7, FALSE, FALSE, 3, 'automatic', TRUE,
                       'Pelanggan terhormat, tagihan Anda sudah melewati jatuh tempo. Layanan akan dinonaktifkan bila belum ada pembayaran.',
                       TRUE, '09:00:00', TRUE
                WHERE NOT EXISTS (SELECT 1 FROM billing_automation_settings)
                """);

        apply("""
                CREATE TABLE IF NOT EXISTS invoice_delivery_logs (
                    id BIGSERIAL PRIMARY KEY,
                    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
                    invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
                    channel VARCHAR(30) NOT NULL,
                    target VARCHAR(100),
                    message_type VARCHAR(30) NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    provider_message_id VARCHAR(160),
                    payload TEXT,
                    response_payload TEXT,
                    error_message TEXT,
                    send_method VARCHAR(30),
                    sent_at TIMESTAMP,
                    sent_by VARCHAR(100),
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        apply("CREATE INDEX IF NOT EXISTS idx_invoice_delivery_logs_invoice ON invoice_delivery_logs(invoice_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_invoice_delivery_logs_customer ON invoice_delivery_logs(customer_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_invoice_delivery_logs_status ON invoice_delivery_logs(status)");
        apply("CREATE INDEX IF NOT EXISTS idx_invoice_delivery_logs_sent_at ON invoice_delivery_logs(sent_at)");

        apply("""
                CREATE TABLE IF NOT EXISTS pppoe_action_logs (
                    id BIGSERIAL PRIMARY KEY,
                    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
                    invoice_id BIGINT REFERENCES invoices(id) ON DELETE SET NULL,
                    payment_id BIGINT REFERENCES payments(id) ON DELETE SET NULL,
                    pppoe_username VARCHAR(100) NOT NULL,
                    action_type VARCHAR(20) NOT NULL,
                    reason TEXT,
                    execution_mode VARCHAR(20) NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    request_payload TEXT,
                    response_payload TEXT,
                    error_message TEXT,
                    executed_by VARCHAR(100),
                    executed_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        apply("CREATE INDEX IF NOT EXISTS idx_pppoe_action_logs_customer ON pppoe_action_logs(customer_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_pppoe_action_logs_status ON pppoe_action_logs(status)");
        apply("CREATE INDEX IF NOT EXISTS idx_pppoe_action_logs_executed_at ON pppoe_action_logs(executed_at)");

        apply("""
                CREATE TABLE IF NOT EXISTS customer_billing_status (
                    id BIGSERIAL PRIMARY KEY,
                    customer_id BIGINT NOT NULL UNIQUE REFERENCES customers(id) ON DELETE CASCADE,
                    current_invoice_status VARCHAR(30),
                    current_payment_status VARCHAR(30),
                    last_invoice_sent_at TIMESTAMP,
                    last_receipt_sent_at TIMESTAMP,
                    overdue_days INT,
                    next_invoice_send_date DATE,
                    eligible_for_disable BOOLEAN NOT NULL DEFAULT FALSE,
                    pppoe_current_state VARCHAR(20),
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        apply("CREATE INDEX IF NOT EXISTS idx_customer_billing_status_invoice_status ON customer_billing_status(current_invoice_status)");
        apply("CREATE INDEX IF NOT EXISTS idx_customer_billing_status_pppoe ON customer_billing_status(pppoe_current_state)");

        apply("""
                CREATE TABLE IF NOT EXISTS scheduler_run_logs (
                    id BIGSERIAL PRIMARY KEY,
                    job_name VARCHAR(80) NOT NULL,
                    execution_mode VARCHAR(20) NOT NULL DEFAULT 'system',
                    status VARCHAR(30) NOT NULL DEFAULT 'running',
                    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    finished_at TIMESTAMP,
                    processed_count INT NOT NULL DEFAULT 0,
                    success_count INT NOT NULL DEFAULT 0,
                    failed_count INT NOT NULL DEFAULT 0,
                    error_summary TEXT,
                    triggered_by VARCHAR(100)
                )
                """);
        apply("CREATE INDEX IF NOT EXISTS idx_scheduler_run_logs_job_name ON scheduler_run_logs(job_name)");
        apply("CREATE INDEX IF NOT EXISTS idx_scheduler_run_logs_started_at ON scheduler_run_logs(started_at)");
        apply("CREATE INDEX IF NOT EXISTS idx_scheduler_run_logs_status ON scheduler_run_logs(status)");
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("Skipping billing scheduler schema sync for SQL [{}]: {}", sql, ex.getMessage());
        }
    }
}
