ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS company_profile_id BIGINT;
ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS issue_date DATE;
ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS subtotal_amount DECIMAL(12,2) DEFAULT 0;
ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS tax_rate DECIMAL(5,2) DEFAULT 0;
ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(12,2) DEFAULT 0;
ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS reference_number VARCHAR(100);
ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS currency_code VARCHAR(10) DEFAULT 'IDR';
ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS document_subtitle VARCHAR(200) DEFAULT 'Document Payment Information';
ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS payment_method_id BIGINT;
ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS bank_account_id BIGINT;
ALTER TABLE IF EXISTS invoices ADD COLUMN IF NOT EXISTS payment_notes TEXT;

UPDATE invoices
SET issue_date = COALESCE(issue_date, created_at::date, billing_month, due_date, CURRENT_DATE);

UPDATE invoices
SET subtotal_amount = COALESCE(subtotal_amount, COALESCE(monthly_fee, 0) + COALESCE(installation_fee, 0) + COALESCE(other_charges, 0)),
    tax_rate = COALESCE(tax_rate, 0),
    tax_amount = COALESCE(tax_amount, GREATEST(COALESCE(total_amount, 0) - (COALESCE(monthly_fee, 0) + COALESCE(installation_fee, 0) + COALESCE(other_charges, 0)), 0)),
    currency_code = COALESCE(NULLIF(currency_code, ''), 'IDR'),
    reference_number = COALESCE(reference_number, invoice_number);

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
);

CREATE TABLE IF NOT EXISTS invoice_qr_codes (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    code_format VARCHAR(50) DEFAULT 'DATA_MATRIX',
    payload TEXT,
    image_data_uri TEXT,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_invoices_company_profile ON invoices(company_profile_id);
CREATE INDEX IF NOT EXISTS idx_invoices_payment_method_ref ON invoices(payment_method_id);
CREATE INDEX IF NOT EXISTS idx_invoices_bank_account_ref ON invoices(bank_account_id);
CREATE INDEX IF NOT EXISTS idx_invoice_items_invoice ON invoice_items(invoice_id);
CREATE INDEX IF NOT EXISTS idx_invoice_qr_codes_invoice ON invoice_qr_codes(invoice_id);
CREATE INDEX IF NOT EXISTS idx_invoice_qr_codes_active ON invoice_qr_codes(is_active);

