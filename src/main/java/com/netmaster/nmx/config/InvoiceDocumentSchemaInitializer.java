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
public class InvoiceDocumentSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS company_profile_id BIGINT REFERENCES company_profiles(id)");
        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS issue_date DATE");
        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS subtotal_amount DECIMAL(12,2) DEFAULT 0");
        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS tax_rate DECIMAL(5,2) DEFAULT 0");
        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(12,2) DEFAULT 0");
        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS reference_number VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS currency_code VARCHAR(10) DEFAULT 'IDR'");
        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS document_subtitle VARCHAR(200) DEFAULT 'Document Payment Information'");
        apply("UPDATE invoices SET issue_date = COALESCE(issue_date, created_at::date, billing_month, due_date, CURRENT_DATE)");
        apply("""
                UPDATE invoices
                SET subtotal_amount = COALESCE(subtotal_amount, COALESCE(monthly_fee, 0) + COALESCE(installation_fee, 0) + COALESCE(other_charges, 0)),
                    tax_rate = COALESCE(tax_rate, 0),
                    tax_amount = COALESCE(tax_amount, GREATEST(COALESCE(total_amount, 0) - (COALESCE(monthly_fee, 0) + COALESCE(installation_fee, 0) + COALESCE(other_charges, 0)), 0)),
                    currency_code = COALESCE(NULLIF(currency_code, ''), 'IDR'),
                    reference_number = COALESCE(reference_number, invoice_number)
                """);

        apply("""
                CREATE TABLE IF NOT EXISTS payment_methods (
                    id BIGSERIAL PRIMARY KEY,
                    company_profile_id BIGINT REFERENCES company_profiles(id) ON DELETE SET NULL,
                    code VARCHAR(50) UNIQUE,
                    name VARCHAR(100) NOT NULL DEFAULT 'Data Belum di Set',
                    channel_type VARCHAR(50) DEFAULT 'Data Belum di Set',
                    instructions TEXT DEFAULT 'Data Belum di Set',
                    notes TEXT DEFAULT 'Data Belum di Set',
                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        apply("""
                CREATE TABLE IF NOT EXISTS bank_accounts (
                    id BIGSERIAL PRIMARY KEY,
                    company_profile_id BIGINT REFERENCES company_profiles(id) ON DELETE SET NULL,
                    payment_method_id BIGINT REFERENCES payment_methods(id) ON DELETE SET NULL,
                    bank_name VARCHAR(120) DEFAULT 'Data Belum di Set',
                    account_name VARCHAR(150) DEFAULT 'Data Belum di Set',
                    account_number VARCHAR(80) DEFAULT 'Data Belum di Set',
                    branch_address TEXT DEFAULT 'Data Belum di Set',
                    swift_code VARCHAR(50) DEFAULT 'Data Belum di Set',
                    payment_reference_label VARCHAR(150) DEFAULT 'Payment Reference',
                    instructions TEXT DEFAULT 'Data Belum di Set',
                    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        apply("""
                CREATE TABLE IF NOT EXISTS company_settings (
                    id BIGSERIAL PRIMARY KEY,
                    company_profile_id BIGINT REFERENCES company_profiles(id) ON DELETE CASCADE,
                    default_currency_code VARCHAR(10) DEFAULT 'IDR',
                    default_locale_code VARCHAR(20) DEFAULT 'id-ID',
                    default_tax_rate DECIMAL(5,2) DEFAULT 0,
                    default_invoice_title VARCHAR(100) DEFAULT 'INVOICE',
                    default_invoice_subtitle VARCHAR(200) DEFAULT 'Document Payment Information',
                    default_footer_note TEXT DEFAULT 'Data Belum di Set',
                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        apply("""
                CREATE TABLE IF NOT EXISTS invoice_items (
                    id BIGSERIAL PRIMARY KEY,
                    invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
                    description VARCHAR(255) NOT NULL DEFAULT 'Data Belum di Set',
                    rate DECIMAL(12,2) NOT NULL DEFAULT 0,
                    quantity DECIMAL(12,2) NOT NULL DEFAULT 1,
                    unit_name VARCHAR(80) NOT NULL DEFAULT 'Data Belum di Set',
                    subtotal DECIMAL(12,2) NOT NULL DEFAULT 0,
                    sort_order INT DEFAULT 0,
                    notes TEXT DEFAULT 'Data Belum di Set',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS payment_method_id BIGINT REFERENCES payment_methods(id)");
        apply("ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS bank_account_id BIGINT REFERENCES bank_accounts(id)");

        apply("CREATE INDEX IF NOT EXISTS idx_invoices_company_profile ON invoices(company_profile_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_invoices_payment_method_ref ON invoices(payment_method_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_invoices_bank_account_ref ON invoices(bank_account_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_payment_methods_company ON payment_methods(company_profile_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_bank_accounts_company ON bank_accounts(company_profile_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_invoice_items_invoice ON invoice_items(invoice_id)");

        apply("""
                INSERT INTO payment_methods (company_profile_id, code, name, channel_type, instructions)
                SELECT cp.id, 'BANK_TRANSFER', 'Transfer Bank', 'BANK_TRANSFER', 'Silakan transfer sesuai nominal invoice.'
                FROM company_profiles cp
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM payment_methods pm
                    WHERE UPPER(COALESCE(pm.code, '')) = 'BANK_TRANSFER'
                )
                """);
        apply("""
                INSERT INTO company_settings (
                    company_profile_id,
                    default_currency_code,
                    default_locale_code,
                    default_tax_rate,
                    default_invoice_title,
                    default_invoice_subtitle,
                    default_footer_note
                )
                SELECT cp.id, 'IDR', 'id-ID', 0, 'INVOICE', 'Document Payment Information',
                       'Invoice ini digenerate otomatis oleh sistem. Jika ada pertanyaan silakan hubungi kontak perusahaan.'
                FROM company_profiles cp
                WHERE NOT EXISTS (
                    SELECT 1 FROM company_settings cs WHERE cs.company_profile_id = cp.id
                )
                """);
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("Skipping invoice document schema sync for SQL [{}]: {}", sql, ex.getMessage());
        }
    }
}
