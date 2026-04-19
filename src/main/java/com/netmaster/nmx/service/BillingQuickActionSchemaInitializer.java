package com.netmaster.nmx.service;

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
public class BillingQuickActionSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        apply("ALTER TABLE IF EXISTS customers ADD COLUMN IF NOT EXISTS name VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS customers ADD COLUMN IF NOT EXISTS pppoe_username VARCHAR(50)");
        apply("ALTER TABLE IF EXISTS customers ADD COLUMN IF NOT EXISTS package_price DECIMAL(12,2) DEFAULT 0");
        apply("ALTER TABLE IF EXISTS customers ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE");
        apply("UPDATE customers SET name = COALESCE(name, full_name) WHERE name IS NULL");
        apply("""
                UPDATE customers c
                SET pppoe_username = cs.pppoe_username,
                    package_price = COALESCE(cs.monthly_fee, 0)
                FROM (
                    SELECT DISTINCT ON (customer_id)
                           customer_id,
                           pppoe_username,
                           monthly_fee
                    FROM customer_services
                    ORDER BY customer_id, created_at DESC, id DESC
                ) cs
                WHERE c.id = cs.customer_id
                """);
        apply("CREATE INDEX IF NOT EXISTS idx_customers_is_deleted ON customers(is_deleted)");

        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS amount DECIMAL(12,2)");
        apply("UPDATE invoices SET amount = COALESCE(total_amount, amount, 0) WHERE amount IS NULL");
        apply("CREATE INDEX IF NOT EXISTS idx_invoices_customer_due_date ON invoices(customer_id, due_date DESC)");

        apply("""
                CREATE TABLE IF NOT EXISTS payment_history (
                    id BIGSERIAL PRIMARY KEY,
                    customer_id BIGINT NOT NULL REFERENCES customers(id),
                    invoice_id BIGINT NOT NULL REFERENCES invoices(id),
                    amount DECIMAL(12,2) NOT NULL,
                    payment_date DATE NOT NULL,
                    method VARCHAR(20) NOT NULL,
                    description VARCHAR(30) NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT uk_payment_history_invoice UNIQUE (invoice_id)
                )
                """);
        apply("CREATE INDEX IF NOT EXISTS idx_payment_history_customer ON payment_history(customer_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_payment_history_invoice ON payment_history(invoice_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_payment_history_payment_date ON payment_history(payment_date)");
        apply("""
                INSERT INTO payment_history (customer_id, invoice_id, amount, payment_date, method, description, created_at)
                SELECT p.customer_id,
                       p.invoice_id,
                       SUM(p.amount_paid) AS amount,
                       MAX(p.payment_date) AS payment_date,
                       COALESCE(MAX(NULLIF(TRIM(p.payment_method), '')), 'CASH') AS method,
                       CASE
                           WHEN LOWER(COALESCE(MAX(i.invoice_type), 'subscription')) = 'activation' THEN 'AKTIVASI'
                           ELSE 'BULANAN'
                       END AS description,
                       COALESCE(MAX(p.created_at), CURRENT_TIMESTAMP) AS created_at
                FROM payments p
                JOIN invoices i ON i.id = p.invoice_id
                LEFT JOIN payment_history ph ON ph.invoice_id = p.invoice_id
                WHERE ph.id IS NULL
                GROUP BY p.customer_id, p.invoice_id
                """);

        apply("""
                CREATE TABLE IF NOT EXISTS activity_logs (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT,
                    action VARCHAR(80) NOT NULL,
                    description TEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        apply("CREATE INDEX IF NOT EXISTS idx_activity_logs_user ON activity_logs(user_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_activity_logs_action ON activity_logs(action)");
        apply("CREATE INDEX IF NOT EXISTS idx_activity_logs_created_at ON activity_logs(created_at DESC)");
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("Skipping quick action billing schema sync for SQL [{}]: {}", sql, ex.getMessage());
        }
    }
}
