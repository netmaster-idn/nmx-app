package com.netmaster.nmx.config;

import com.netmaster.nmx.service.PaymentManagementService;
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
public class BillingSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PaymentManagementService paymentManagementService;

    @Override
    public void run(String... args) {
        apply("""
                CREATE TABLE IF NOT EXISTS payments (
                    id BIGSERIAL PRIMARY KEY,
                    invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
                    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
                    amount_paid DECIMAL(12,2) NOT NULL,
                    payment_date DATE NOT NULL,
                    payment_method VARCHAR(50),
                    reference_number VARCHAR(100),
                    notes TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        apply("CREATE INDEX IF NOT EXISTS idx_payments_invoice ON payments(invoice_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_payments_customer ON payments(customer_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_payments_date ON payments(payment_date)");

        try {
            paymentManagementService.backfillLegacyInvoicePayments();
        } catch (Exception ex) {
            log.warn("Skipping payment backfill: {}", ex.getMessage());
        }

        try {
            paymentManagementService.backfillActivationPaymentsFromFirstCustomerPayment();
        } catch (Exception ex) {
            log.warn("Skipping activation payment backfill: {}", ex.getMessage());
        }
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("Skipping billing schema sync for SQL [{}]: {}", sql, ex.getMessage());
        }
    }
}
