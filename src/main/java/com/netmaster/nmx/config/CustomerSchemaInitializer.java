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
public class CustomerSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        apply("ALTER TABLE IF EXISTS customers ALTER COLUMN ktp_number DROP NOT NULL");
        apply("ALTER TABLE IF EXISTS customers ALTER COLUMN region_id DROP NOT NULL");
        apply("ALTER TABLE IF EXISTS customers ALTER COLUMN latitude DROP NOT NULL");
        apply("ALTER TABLE IF EXISTS customers ALTER COLUMN longitude DROP NOT NULL");
        apply("ALTER TABLE IF EXISTS customer_services ALTER COLUMN odp_id DROP NOT NULL");
        apply("ALTER TABLE IF EXISTS customer_services ALTER COLUMN odp_port DROP NOT NULL");
        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS invoice_type VARCHAR(30)");
        apply("""
                UPDATE customer_services cs
                SET pppoe_username = LEFT(TRIM(cs.pppoe_username) || '-DEL' || cs.id, 50)
                FROM customers c
                WHERE cs.customer_id = c.id
                  AND COALESCE(c.is_deleted, false) = true
                  AND cs.pppoe_username IS NOT NULL
                  AND TRIM(cs.pppoe_username) <> ''
                  AND cs.pppoe_username NOT LIKE '%-DEL%'
                """);
        apply("""
                UPDATE customer_services cs
                SET ont_serial = LEFT(TRIM(cs.ont_serial) || '-DEL' || cs.id, 50)
                FROM customers c
                WHERE cs.customer_id = c.id
                  AND COALESCE(c.is_deleted, false) = true
                  AND cs.ont_serial IS NOT NULL
                  AND TRIM(cs.ont_serial) <> ''
                  AND cs.ont_serial NOT LIKE '%-DEL%'
                """);
        apply("""
                UPDATE customers
                SET ktp_number = LEFT(TRIM(ktp_number) || '-DEL' || id, 20)
                WHERE COALESCE(is_deleted, false) = true
                  AND ktp_number IS NOT NULL
                  AND TRIM(ktp_number) <> ''
                  AND ktp_number NOT LIKE '%-DEL%'
                """);
        apply("""
                UPDATE customers
                SET customer_code = LEFT(TRIM(customer_code) || '-DEL' || id, 20)
                WHERE COALESCE(is_deleted, false) = true
                  AND customer_code IS NOT NULL
                  AND TRIM(customer_code) <> ''
                  AND customer_code NOT LIKE '%-DEL%'
                """);
        apply("UPDATE invoices SET invoice_type = 'activation' WHERE invoice_type IS NULL AND (LOWER(COALESCE(notes, '')) LIKE '%aktivasi%' OR LOWER(COALESCE(payment_notes, '')) LIKE '%aktivasi%')");
        apply("UPDATE invoices SET invoice_type = 'subscription' WHERE invoice_type IS NULL");
        apply("""
                UPDATE invoices
                SET monthly_fee = 0,
                    total_amount = COALESCE(installation_fee, 0) + COALESCE(other_charges, 0),
                    amount_paid = CASE
                        WHEN LOWER(COALESCE(status, '')) = 'paid' THEN COALESCE(installation_fee, 0) + COALESCE(other_charges, 0)
                        ELSE LEAST(COALESCE(amount_paid, 0), COALESCE(installation_fee, 0) + COALESCE(other_charges, 0))
                    END
                WHERE LOWER(COALESCE(invoice_type, '')) = 'activation'
                """);
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("Skipping customer schema sync for SQL [{}]: {}", sql, ex.getMessage());
        }
    }
}
