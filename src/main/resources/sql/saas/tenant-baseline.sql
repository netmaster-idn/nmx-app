CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    permissions_level VARCHAR(20) NOT NULL DEFAULT 'READ'
);

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    employee_id VARCHAR(50),
    full_name VARCHAR(150),
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(150),
    phone VARCHAR(30),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id),
    role_id BIGINT NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS customers (
    id BIGSERIAL PRIMARY KEY,
    customer_code VARCHAR(50),
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(150),
    phone VARCHAR(30) NOT NULL,
    installation_address TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    registration_date DATE DEFAULT CURRENT_DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS internet_packages (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    speed_down INTEGER,
    speed_up INTEGER,
    price NUMERIC(12,2) NOT NULL DEFAULT 0,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS invoices (
    id BIGSERIAL PRIMARY KEY,
    invoice_number VARCHAR(50) UNIQUE,
    customer_id BIGINT REFERENCES customers(id),
    issue_date DATE DEFAULT CURRENT_DATE,
    due_date DATE,
    total_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
    amount_paid NUMERIC(12,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'pending',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id),
    customer_id BIGINT NOT NULL REFERENCES customers(id),
    amount_paid NUMERIC(12,2) NOT NULL DEFAULT 0,
    payment_date DATE NOT NULL DEFAULT CURRENT_DATE,
    payment_method VARCHAR(50),
    status VARCHAR(20) DEFAULT 'paid',
    reference_number VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS network_devices (
    id BIGSERIAL PRIMARY KEY,
    device_name VARCHAR(100) NOT NULL,
    device_type VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    location VARCHAR(100),
    vendor VARCHAR(50),
    model VARCHAR(50),
    status VARCHAR(20),
    is_monitored BOOLEAN DEFAULT TRUE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS network_alerts (
    id BIGSERIAL PRIMARY KEY,
    alert_id VARCHAR(50) UNIQUE,
    device_id BIGINT,
    device_name VARCHAR(100),
    device_type VARCHAR(50),
    device_ip VARCHAR(45),
    location VARCHAR(100),
    alert_type VARCHAR(50),
    metric_type VARCHAR(50),
    metric_value DOUBLE PRECISION,
    threshold DOUBLE PRECISION,
    duration_minutes INTEGER,
    severity VARCHAR(20),
    status VARCHAR(20) DEFAULT 'active',
    source VARCHAR(20),
    message VARCHAR(500),
    assigned_engineer VARCHAR(100),
    affected_customers INTEGER,
    affected_service VARCHAR(100),
    sla_impact VARCHAR(20),
    is_acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_by BIGINT REFERENCES users(id),
    acknowledged_at TIMESTAMP,
    acknowledged_notes VARCHAR(500),
    is_resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP,
    resolved_notes VARCHAR(500),
    is_closed BOOLEAN DEFAULT FALSE,
    closed_at TIMESTAMP,
    investigation_notes VARCHAR(1000),
    occurrence_count INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS alert_id VARCHAR(50);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS device_id BIGINT;
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS device_name VARCHAR(100);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS device_type VARCHAR(50);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS device_ip VARCHAR(45);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS location VARCHAR(100);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS alert_type VARCHAR(50);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS metric_type VARCHAR(50);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS metric_value DOUBLE PRECISION;
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS threshold DOUBLE PRECISION;
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS duration_minutes INTEGER;
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS severity VARCHAR(20);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS source VARCHAR(20);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS message VARCHAR(500);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS assigned_engineer VARCHAR(100);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS affected_customers INTEGER;
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS affected_service VARCHAR(100);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS sla_impact VARCHAR(20);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS is_acknowledged BOOLEAN DEFAULT FALSE;
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS acknowledged_by BIGINT;
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMP;
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS acknowledged_notes VARCHAR(500);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS is_resolved BOOLEAN DEFAULT FALSE;
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP;
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS resolved_notes VARCHAR(500);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS is_closed BOOLEAN DEFAULT FALSE;
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS investigation_notes VARCHAR(1000);
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS occurrence_count INTEGER DEFAULT 1;
ALTER TABLE network_alerts
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'network_alerts' AND column_name = 'value'
    ) THEN
        UPDATE network_alerts
        SET metric_value = value
        WHERE metric_value IS NULL AND value IS NOT NULL;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'network_alerts' AND column_name = 'acknowledged_by_id'
    ) THEN
        UPDATE network_alerts
        SET acknowledged_by = acknowledged_by_id
        WHERE acknowledged_by IS NULL AND acknowledged_by_id IS NOT NULL;
    END IF;
END $$;

UPDATE network_alerts SET status = 'active' WHERE status IS NULL;
UPDATE network_alerts SET severity = 'warning' WHERE severity IS NULL;
UPDATE network_alerts SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;
UPDATE network_alerts SET updated_at = COALESCE(updated_at, created_at, CURRENT_TIMESTAMP);
UPDATE network_alerts
SET alert_id = CONCAT('ALT-', TO_CHAR(COALESCE(created_at, CURRENT_TIMESTAMP), 'YYYYMMDD'), '-', LPAD(id::text, 3, '0'))
WHERE alert_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_network_alerts_alert_id ON network_alerts(alert_id);
CREATE INDEX IF NOT EXISTS idx_network_alerts_status ON network_alerts(status);
CREATE INDEX IF NOT EXISTS idx_network_alerts_severity ON network_alerts(severity);

CREATE TABLE IF NOT EXISTS olt_devices (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45),
    vpn_ip_address VARCHAR(45),
    username VARCHAR(50),
    password VARCHAR(100),
    vendor VARCHAR(50),
    model VARCHAR(50),
    serial_number VARCHAR(50),
    slot_count INTEGER,
    pon_port_count INTEGER,
    gpon_port_count INTEGER,
    location VARCHAR(200),
    description VARCHAR(500),
    status VARCHAR(20) DEFAULT 'offline',
    onu_count INTEGER,
    optical_rx_power DOUBLE PRECISION,
    optical_tx_power DOUBLE PRECISION,
    temperature DOUBLE PRECISION,
    voltage DOUBLE PRECISION,
    last_monitored TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS acs_devices (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    acs_device_id VARCHAR(255) UNIQUE,
    ip_address VARCHAR(15),
    mac_address VARCHAR(17),
    device_type VARCHAR(50),
    vendor VARCHAR(50),
    model VARCHAR(50),
    serial_number VARCHAR(50),
    firmware_version VARCHAR(50),
    software_version VARCHAR(50),
    olt_id BIGINT REFERENCES olt_devices(id),
    olt_port INTEGER,
    onu_id INTEGER,
    optical_rx_power DOUBLE PRECISION,
    optical_tx_power DOUBLE PRECISION,
    optical_temperature DOUBLE PRECISION,
    optical_voltage DOUBLE PRECISION,
    wifi_name VARCHAR(100),
    status VARCHAR(20) DEFAULT 'offline',
    connection_type VARCHAR(20),
    distance INTEGER,
    last_acs_request TIMESTAMP,
    last_inform TIMESTAMP,
    is_provisioned BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_synced_at TIMESTAMP
);

ALTER TABLE acs_devices
    ADD COLUMN IF NOT EXISTS acs_device_id VARCHAR(255);
ALTER TABLE acs_devices
    ADD COLUMN IF NOT EXISTS wifi_name VARCHAR(100);
ALTER TABLE acs_devices
    ADD COLUMN IF NOT EXISTS last_synced_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS idx_acs_devices_acs_device_id ON acs_devices(acs_device_id);

CREATE TABLE IF NOT EXISTS acs_settings (
    id BIGSERIAL PRIMARY KEY,
    server_url VARCHAR(255),
    username VARCHAR(120),
    password VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    last_connection_status VARCHAR(30),
    last_connection_message TEXT,
    last_connected_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO acs_settings (id, is_active, created_at, updated_at)
VALUES (1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS tickets (
    id BIGSERIAL PRIMARY KEY,
    ticket_number VARCHAR(50) UNIQUE,
    customer_id BIGINT REFERENCES customers(id),
    subject VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'open',
    priority VARCHAR(20) DEFAULT 'medium',
    category VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS technician_id BIGINT;

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS sla_deadline TIMESTAMP;

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP;

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP;

ALTER TABLE tickets
    ADD COLUMN IF NOT EXISTS resolution_notes TEXT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'tickets' AND column_name = 'assigned_technician_id'
    ) THEN
        UPDATE tickets
        SET technician_id = COALESCE(technician_id, assigned_technician_id)
        WHERE technician_id IS NULL AND assigned_technician_id IS NOT NULL;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS projects (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(30) DEFAULT 'draft',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS monitoring_logs (
    id BIGSERIAL PRIMARY KEY,
    device_name VARCHAR(150),
    log_level VARCHAR(20),
    message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS settings (
    id BIGSERIAL PRIMARY KEY,
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS regions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS company_profiles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL DEFAULT 'Tenant Company',
    address VARCHAR(500),
    province_code VARCHAR(20),
    province_name VARCHAR(100),
    regency_code VARCHAR(20),
    regency_name VARCHAR(100),
    district_code VARCHAR(20),
    district_name VARCHAR(100),
    village_code VARCHAR(20),
    village_name VARCHAR(100),
    rt VARCHAR(10),
    rw VARCHAR(10),
    building_number VARCHAR(50),
    street_name VARCHAR(200),
    google_maps_coordinates VARCHAR(100),
    phone VARCHAR(20),
    email VARCHAR(100),
    website VARCHAR(100),
    logo VARCHAR(500),
    npwp VARCHAR(50),
    pkp_number VARCHAR(50),
    business_type VARCHAR(200),
    tagline VARCHAR(200),
    facebook TEXT,
    instagram TEXT,
    twitter TEXT,
    whatsapp VARCHAR(20),
    support_email VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS payment_methods (
    id BIGSERIAL PRIMARY KEY,
    company_profile_id BIGINT,
    code VARCHAR(50) UNIQUE,
    name VARCHAR(100) NOT NULL DEFAULT 'Data Belum di Set',
    channel_type VARCHAR(50),
    instructions TEXT,
    notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS bank_accounts (
    id BIGSERIAL PRIMARY KEY,
    company_profile_id BIGINT,
    payment_method_id BIGINT,
    bank_name VARCHAR(120),
    account_name VARCHAR(150),
    account_number VARCHAR(80),
    branch_address TEXT,
    swift_code VARCHAR(50),
    payment_reference_label VARCHAR(150),
    instructions TEXT,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS service_types (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    service_type VARCHAR(50),
    authentication_method VARCHAR(20) DEFAULT 'CHAP',
    support_pppoe BOOLEAN DEFAULT FALSE,
    support_hotspot BOOLEAN DEFAULT FALSE,
    support_static_ip BOOLEAN DEFAULT FALSE,
    support_dhcp BOOLEAN DEFAULT FALSE,
    support_olt BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS technicians (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    area VARCHAR(100),
    email VARCHAR(100),
    employee_id VARCHAR(20) UNIQUE,
    status VARCHAR(20) DEFAULT 'active',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS servers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    ip_address VARCHAR(15) NOT NULL,
    location VARCHAR(255),
    mikrotik_id BIGINT,
    region_id BIGINT,
    latitude NUMERIC(10,8),
    longitude NUMERIC(11,8),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS odcs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) UNIQUE,
    server_id BIGINT,
    location VARCHAR(255),
    latitude NUMERIC(10,8),
    longitude NUMERIC(11,8),
    capacity INTEGER DEFAULT 32,
    used_port INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS odps (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) UNIQUE,
    odc_id BIGINT,
    company_profile_id BIGINT,
    node_type VARCHAR(10) DEFAULT 'ODP',
    location VARCHAR(255),
    splitter VARCHAR(100),
    latitude NUMERIC(10,8),
    longitude NUMERIC(11,8),
    capacity INTEGER DEFAULT 8,
    used_port INTEGER DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS customer_services (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    package_id BIGINT,
    service_type_id BIGINT,
    odp_id BIGINT,
    technician_id BIGINT,
    odp_port INTEGER,
    ont_serial VARCHAR(50),
    ont_brand VARCHAR(50),
    ont_redaman NUMERIC(6,2),
    wifi_name VARCHAR(100),
    wifi_password VARCHAR(100),
    ont_standby_since TIMESTAMP,
    last_restart_requested_at TIMESTAMP,
    pppoe_username VARCHAR(50),
    pppoe_password VARCHAR(100),
    ip_address VARCHAR(15),
    mac_address VARCHAR(17),
    monthly_fee NUMERIC(12,2) NOT NULL DEFAULT 0,
    installation_fee NUMERIC(12,2) DEFAULT 0,
    installation_date DATE,
    activation_date DATE,
    status VARCHAR(20) DEFAULT 'pending',
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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

ALTER TABLE customers ADD COLUMN IF NOT EXISTS whatsapp_number VARCHAR(30);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS ktp_number VARCHAR(20);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS ktp_address TEXT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS region_id BIGINT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS latitude NUMERIC(10,8);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS longitude NUMERIC(11,8);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS billing_due_day INTEGER;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS pppoe_status VARCHAR(20) DEFAULT 'unknown';
ALTER TABLE customers ADD COLUMN IF NOT EXISTS notes TEXT;

ALTER TABLE internet_packages ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE internet_packages ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS customer_service_id BIGINT;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS company_profile_id BIGINT;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS payment_method_id BIGINT;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS bank_account_id BIGINT;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS billing_month DATE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS monthly_fee NUMERIC(12,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS installation_fee NUMERIC(12,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS other_charges NUMERIC(12,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS subtotal_amount NUMERIC(12,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS tax_rate NUMERIC(5,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS sent_at TIMESTAMP;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS send_method VARCHAR(30);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS whatsapp_status VARCHAR(30);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS last_reminder_at TIMESTAMP;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS payment_date DATE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS payment_method VARCHAR(50);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS reference_number VARCHAR(100);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS invoice_type VARCHAR(30) DEFAULT 'subscription';
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS currency_code VARCHAR(10) DEFAULT 'IDR';
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS document_subtitle VARCHAR(200);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS payment_notes TEXT;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS notes TEXT;

ALTER TABLE payments ADD COLUMN IF NOT EXISTS receipt_sent_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS receipt_send_method VARCHAR(30);

ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS firmware_version VARCHAR(50);
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS serial_number VARCHAR(100);
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS mac_address VARCHAR(17);
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS uptime_seconds BIGINT;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS last_ping_time TIMESTAMP;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS last_snmp_time TIMESTAMP;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS ping_interval INTEGER DEFAULT 30;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS snmp_interval INTEGER DEFAULT 60;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS snmp_community VARCHAR(100);
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS snmp_version VARCHAR(10);
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS api_port INTEGER;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS api_username VARCHAR(100);
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS source_type VARCHAR(50);
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS source_id BIGINT;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS cpu_warning_threshold INTEGER DEFAULT 70;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS cpu_critical_threshold INTEGER DEFAULT 90;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS memory_warning_threshold INTEGER DEFAULT 80;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS memory_critical_threshold INTEGER DEFAULT 95;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS temperature_warning_threshold INTEGER DEFAULT 45;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS temperature_critical_threshold INTEGER DEFAULT 60;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS parent_device_id BIGINT;
ALTER TABLE network_devices ADD COLUMN IF NOT EXISTS notes TEXT;

CREATE INDEX IF NOT EXISTS idx_customer_services_customer_id ON customer_services(customer_id);
CREATE INDEX IF NOT EXISTS idx_customer_services_pppoe_username ON customer_services(pppoe_username);
CREATE INDEX IF NOT EXISTS idx_invoices_customer_service_id ON invoices(customer_service_id);
CREATE INDEX IF NOT EXISTS idx_payment_history_customer ON payment_history(customer_id);
CREATE INDEX IF NOT EXISTS idx_payment_history_invoice ON payment_history(invoice_id);
CREATE INDEX IF NOT EXISTS idx_payment_history_payment_date ON payment_history(payment_date);
CREATE INDEX IF NOT EXISTS idx_invoice_delivery_logs_invoice ON invoice_delivery_logs(invoice_id);
CREATE INDEX IF NOT EXISTS idx_invoice_delivery_logs_customer ON invoice_delivery_logs(customer_id);
CREATE INDEX IF NOT EXISTS idx_invoice_delivery_logs_status ON invoice_delivery_logs(status);
CREATE INDEX IF NOT EXISTS idx_invoice_delivery_logs_sent_at ON invoice_delivery_logs(sent_at);
CREATE INDEX IF NOT EXISTS idx_pppoe_action_logs_customer ON pppoe_action_logs(customer_id);
CREATE INDEX IF NOT EXISTS idx_pppoe_action_logs_status ON pppoe_action_logs(status);
CREATE INDEX IF NOT EXISTS idx_pppoe_action_logs_executed_at ON pppoe_action_logs(executed_at);
CREATE INDEX IF NOT EXISTS idx_customer_billing_status_invoice_status ON customer_billing_status(current_invoice_status);
CREATE INDEX IF NOT EXISTS idx_customer_billing_status_pppoe ON customer_billing_status(pppoe_current_state);
CREATE INDEX IF NOT EXISTS idx_scheduler_run_logs_job_name ON scheduler_run_logs(job_name);
CREATE INDEX IF NOT EXISTS idx_scheduler_run_logs_started_at ON scheduler_run_logs(started_at);
CREATE INDEX IF NOT EXISTS idx_scheduler_run_logs_status ON scheduler_run_logs(status);
