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
);

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
SELECT FALSE,
       7,
       FALSE,
       FALSE,
       3,
       'automatic',
       TRUE,
       'Pelanggan terhormat, tagihan Anda sudah melewati jatuh tempo. Layanan akan dinonaktifkan bila belum ada pembayaran.',
       TRUE,
       '09:00:00',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM billing_automation_settings);

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
);

CREATE INDEX IF NOT EXISTS idx_payment_history_customer ON payment_history(customer_id);
CREATE INDEX IF NOT EXISTS idx_payment_history_invoice ON payment_history(invoice_id);
CREATE INDEX IF NOT EXISTS idx_payment_history_payment_date ON payment_history(payment_date);

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
GROUP BY p.customer_id, p.invoice_id;

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
);

CREATE INDEX IF NOT EXISTS idx_invoice_delivery_logs_invoice ON invoice_delivery_logs(invoice_id);
CREATE INDEX IF NOT EXISTS idx_invoice_delivery_logs_customer ON invoice_delivery_logs(customer_id);
CREATE INDEX IF NOT EXISTS idx_invoice_delivery_logs_status ON invoice_delivery_logs(status);
CREATE INDEX IF NOT EXISTS idx_invoice_delivery_logs_sent_at ON invoice_delivery_logs(sent_at);

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
);

CREATE INDEX IF NOT EXISTS idx_pppoe_action_logs_customer ON pppoe_action_logs(customer_id);
CREATE INDEX IF NOT EXISTS idx_pppoe_action_logs_status ON pppoe_action_logs(status);
CREATE INDEX IF NOT EXISTS idx_pppoe_action_logs_executed_at ON pppoe_action_logs(executed_at);

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
);

CREATE INDEX IF NOT EXISTS idx_customer_billing_status_invoice_status ON customer_billing_status(current_invoice_status);
CREATE INDEX IF NOT EXISTS idx_customer_billing_status_pppoe ON customer_billing_status(pppoe_current_state);

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
);

CREATE INDEX IF NOT EXISTS idx_scheduler_run_logs_job_name ON scheduler_run_logs(job_name);
CREATE INDEX IF NOT EXISTS idx_scheduler_run_logs_started_at ON scheduler_run_logs(started_at);
CREATE INDEX IF NOT EXISTS idx_scheduler_run_logs_status ON scheduler_run_logs(status);
