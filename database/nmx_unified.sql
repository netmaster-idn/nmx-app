-- =====================================================
-- NMX UNIFIED DATABASE SCHEMA
-- Consolidated from multiple SQL files:
-- - nmx_complete.sql (base)
-- - user_role.sql
-- - monitoring_schema.sql
-- - new_modules.sql
--
-- Database: PostgreSQL
-- =====================================================

-- Enable UUID extension
DO $$ BEGIN
    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- =====================================================
-- CORE MODULE: Service Types
-- =====================================================
CREATE TABLE IF NOT EXISTS service_types (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
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

-- =====================================================
-- CORE MODULE: Regions
-- =====================================================
CREATE TABLE IF NOT EXISTS regions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- CORE MODULE: Internet Packages
-- =====================================================
CREATE TABLE IF NOT EXISTS internet_packages (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    speed_down INT NOT NULL,
    speed_up INT NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT true,
    burst_download INT,
    burst_upload INT,
    mikrotik_profile_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- CORE MODULE: Servers (OLT)
-- =====================================================
CREATE TABLE IF NOT EXISTS servers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    ip_address VARCHAR(15) NOT NULL,
    location VARCHAR(255),
    region_id BIGINT REFERENCES regions(id),
    latitude DECIMAL(10,8),
    longitude DECIMAL(11,8),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- CORE MODULE: ODC (Optical Distribution Cabinet)
-- =====================================================
CREATE TABLE IF NOT EXISTS odcs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) UNIQUE,
    server_id BIGINT REFERENCES servers(id),
    location VARCHAR(255),
    latitude DECIMAL(10,8),
    longitude DECIMAL(11,8),
    capacity INT NOT NULL DEFAULT 32,
    used_port INT DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- CORE MODULE: ODP (Optical Distribution Point)
-- =====================================================
CREATE TABLE IF NOT EXISTS odps (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(20) UNIQUE,
    odc_id BIGINT REFERENCES odcs(id),
    company_profile_id BIGINT,
    node_type VARCHAR(10) DEFAULT 'ODP',
    location VARCHAR(255),
    splitter VARCHAR(100),
    latitude DECIMAL(10,8),
    longitude DECIMAL(11,8),
    capacity INT NOT NULL DEFAULT 8,
    used_port INT DEFAULT 0,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- CORE MODULE: Customers
-- =====================================================
  CREATE TABLE IF NOT EXISTS customers (
      id BIGSERIAL PRIMARY KEY,
      customer_code VARCHAR(20) UNIQUE NOT NULL,
      full_name VARCHAR(100) NOT NULL,
      email VARCHAR(100),
      phone VARCHAR(20) NOT NULL,
      ktp_number VARCHAR(20) UNIQUE,
    ktp_address TEXT,
    installation_address TEXT NOT NULL,
    region_id BIGINT REFERENCES regions(id),
    latitude DECIMAL(10,8),
    longitude DECIMAL(11,8),
    status VARCHAR(20) DEFAULT 'pending',
    registration_date DATE DEFAULT CURRENT_DATE,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );

  ALTER TABLE customers
      ALTER COLUMN ktp_number DROP NOT NULL;
  ALTER TABLE customers
      ALTER COLUMN region_id DROP NOT NULL;
  ALTER TABLE customers
      ALTER COLUMN latitude DROP NOT NULL;
  ALTER TABLE customers
      ALTER COLUMN longitude DROP NOT NULL;

-- Create indexes
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_customers_phone') THEN
        CREATE INDEX idx_customers_phone ON customers(phone);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_customers_email') THEN
        CREATE INDEX idx_customers_email ON customers(email);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_customers_status') THEN
        CREATE INDEX idx_customers_status ON customers(status);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_customers_region') THEN
        CREATE INDEX idx_customers_region ON customers(region_id);
    END IF;
END $$;

-- =====================================================
-- CORE MODULE: Customer Services
-- =====================================================
  CREATE TABLE IF NOT EXISTS customer_services (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT REFERENCES customers(id) ON DELETE CASCADE,
    package_id BIGINT REFERENCES internet_packages(id),
    service_type_id BIGINT REFERENCES service_types(id),
    odp_id BIGINT REFERENCES odps(id),
    odp_port INT,
    ont_serial VARCHAR(50),
    ont_brand VARCHAR(50),
    ont_redaman DECIMAL(6,2),
    wifi_name VARCHAR(100),
    wifi_password VARCHAR(100),
    ont_standby_since TIMESTAMP,
    last_restart_requested_at TIMESTAMP,
    pppoe_username VARCHAR(50) UNIQUE,
    pppoe_password VARCHAR(100),
    ip_address VARCHAR(15),
    mac_address VARCHAR(17),
    monthly_fee DECIMAL(12,2) NOT NULL,
    installation_fee DECIMAL(12,2) DEFAULT 0,
    installation_date DATE,
    activation_date DATE,
    status VARCHAR(20) DEFAULT 'pending',
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
      updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
  );

  ALTER TABLE customer_services
      ALTER COLUMN odp_id DROP NOT NULL;
  ALTER TABLE customer_services
      ALTER COLUMN odp_port DROP NOT NULL;

-- Create indexes
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_customer_services_customer') THEN
        CREATE INDEX idx_customer_services_customer ON customer_services(customer_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_customer_services_package') THEN
        CREATE INDEX idx_customer_services_package ON customer_services(package_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_customer_services_odp') THEN
        CREATE INDEX idx_customer_services_odp ON customer_services(odp_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_customer_services_status') THEN
        CREATE INDEX idx_customer_services_status ON customer_services(status);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_customer_services_pppoe') THEN
        CREATE INDEX idx_customer_services_pppoe ON customer_services(pppoe_username);
    END IF;
END $$;

-- =====================================================
-- CORE MODULE: Network Topology Links
-- =====================================================
CREATE TABLE IF NOT EXISTS network_topology_links (
    id BIGSERIAL PRIMARY KEY,
    from_node_type VARCHAR(20) NOT NULL,
    from_node_id BIGINT NOT NULL,
    to_node_type VARCHAR(20) NOT NULL,
    to_node_id BIGINT NOT NULL,
    line_color VARCHAR(20) DEFAULT '#fb923c',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_network_topology_links_pair') THEN
        CREATE UNIQUE INDEX idx_network_topology_links_pair
            ON network_topology_links(from_node_type, from_node_id, to_node_type, to_node_id);
    END IF;
END $$;

-- =====================================================
-- CORE MODULE: ONT Action Logs
-- =====================================================
CREATE TABLE IF NOT EXISTS ont_action_logs (
    id BIGSERIAL PRIMARY KEY,
    customer_service_id BIGINT NOT NULL REFERENCES customer_services(id) ON DELETE CASCADE,
    action_type VARCHAR(40) NOT NULL,
    payload TEXT,
    requested_by VARCHAR(100),
    status VARCHAR(30) DEFAULT 'queued',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ont_action_logs_service ON ont_action_logs(customer_service_id);

-- =====================================================
-- CORE MODULE: Technicians
-- =====================================================
CREATE TABLE IF NOT EXISTS technicians (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    area VARCHAR(100),
    email VARCHAR(100),
    employee_id VARCHAR(20) UNIQUE,
    status VARCHAR(20) DEFAULT 'active',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- AUTH MODULE: Roles
-- =====================================================
CREATE TABLE IF NOT EXISTS roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    permissions_level VARCHAR(20) NOT NULL DEFAULT 'READ',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- AUTH MODULE: Users
-- =====================================================
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    employee_id VARCHAR(20) UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(20),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes for users
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_employee ON users(employee_id);

-- =====================================================
-- AUTH MODULE: User Roles (Many-to-Many)
-- =====================================================
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(50),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX IF NOT EXISTS idx_user_roles_user ON user_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles(role_id);

-- =====================================================
-- FINANCE MODULE: Invoices
-- =====================================================
CREATE TABLE IF NOT EXISTS invoices (
    id BIGSERIAL PRIMARY KEY,
    invoice_number VARCHAR(50) UNIQUE NOT NULL,
    customer_id BIGINT REFERENCES customers(id),
    customer_service_id BIGINT REFERENCES customer_services(id),
    billing_month DATE,
    due_date DATE,
    monthly_fee DECIMAL(12,2) DEFAULT 0,
    installation_fee DECIMAL(12,2) DEFAULT 0,
    other_charges DECIMAL(12,2) DEFAULT 0,
    total_amount DECIMAL(12,2) NOT NULL,
    amount_paid DECIMAL(12,2) DEFAULT 0,
    status VARCHAR(20) DEFAULT 'pending',
    payment_date DATE,
    payment_method VARCHAR(50),
    payment_notes TEXT,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_invoices_customer') THEN
        CREATE INDEX idx_invoices_customer ON invoices(customer_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_invoices_status') THEN
        CREATE INDEX idx_invoices_status ON invoices(status);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_invoices_due_date') THEN
        CREATE INDEX idx_invoices_due_date ON invoices(due_date);
    END IF;
END $$;

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
);

CREATE INDEX IF NOT EXISTS idx_payments_invoice ON payments(invoice_id);
CREATE INDEX IF NOT EXISTS idx_payments_customer ON payments(customer_id);
CREATE INDEX IF NOT EXISTS idx_payments_date ON payments(payment_date);
-- =====================================================
-- COMPANY & SYSTEM MODULE (moved earlier for finance dependencies)
-- =====================================================
CREATE TABLE IF NOT EXISTS company_profiles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
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
    facebook VARCHAR(100),
    instagram VARCHAR(100),
    twitter VARCHAR(100),
    whatsapp VARCHAR(20),
    support_email VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- =====================================================
-- FINANCE MODULE: Invoice Document Extensions
-- =====================================================
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS company_profile_id BIGINT REFERENCES company_profiles(id);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS issue_date DATE;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS subtotal_amount DECIMAL(12,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS tax_rate DECIMAL(5,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS tax_amount DECIMAL(12,2) DEFAULT 0;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS reference_number VARCHAR(100);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS currency_code VARCHAR(10) DEFAULT 'IDR';
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS document_subtitle VARCHAR(200) DEFAULT 'Document Payment Information';

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
);

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
);

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
);

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
    notes TEXT DEFAULT 'Data Belum di Set',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS payment_method_id BIGINT REFERENCES payment_methods(id);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS bank_account_id BIGINT REFERENCES bank_accounts(id);

CREATE INDEX IF NOT EXISTS idx_invoices_company_profile ON invoices(company_profile_id);
CREATE INDEX IF NOT EXISTS idx_invoices_payment_method_ref ON invoices(payment_method_id);
CREATE INDEX IF NOT EXISTS idx_invoices_bank_account_ref ON invoices(bank_account_id);
CREATE INDEX IF NOT EXISTS idx_payment_methods_company ON payment_methods(company_profile_id);
CREATE INDEX IF NOT EXISTS idx_bank_accounts_company ON bank_accounts(company_profile_id);
CREATE INDEX IF NOT EXISTS idx_invoice_items_invoice ON invoice_items(invoice_id);
CREATE INDEX IF NOT EXISTS idx_invoice_qr_codes_invoice ON invoice_qr_codes(invoice_id);

-- =====================================================
-- TICKETING MODULE: Tickets
-- =====================================================
CREATE TABLE IF NOT EXISTS tickets (
    id BIGSERIAL PRIMARY KEY,
    ticket_number VARCHAR(50) UNIQUE NOT NULL,
    customer_id BIGINT REFERENCES customers(id),
    subject VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'open',
    priority VARCHAR(20) DEFAULT 'medium',
    category VARCHAR(50),
    assigned_technician_id BIGINT REFERENCES technicians(id),
    created_by_id BIGINT REFERENCES users(id),
    sla_deadline TIMESTAMP,
    resolved_at TIMESTAMP,
    closed_at TIMESTAMP,
    resolution_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- NETWORK MODULE: Mikrotik Devices
-- =====================================================
CREATE TABLE IF NOT EXISTS mikrotik_devices (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    ip_address VARCHAR(45),
    vpn_ip_address VARCHAR(45),
    vpn_port INT DEFAULT 8728,
    username VARCHAR(50),
    password VARCHAR(100),
    location VARCHAR(200),
    description VARCHAR(500),
    status VARCHAR(20) DEFAULT 'offline',
    routerboard_version VARCHAR(50),
    ros_version VARCHAR(50),
    cpu_load INT,
    memory_used BIGINT,
    memory_total BIGINT,
    uptime_seconds BIGINT,
    last_monitored TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- NETWORK MODULE: OLT Devices
-- =====================================================
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
    slot_count INT,
    pon_port_count INT,
    gpon_port_count INT,
    location VARCHAR(200),
    description VARCHAR(500),
    status VARCHAR(20) DEFAULT 'offline',
    onu_count INT,
    optical_rx_power DOUBLE PRECISION,
    optical_tx_power DOUBLE PRECISION,
    temperature DOUBLE PRECISION,
    voltage DOUBLE PRECISION,
    last_monitored TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- NETWORK MODULE: VPN Connections
-- =====================================================
CREATE TABLE IF NOT EXISTS vpn_connections (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    vpn_type VARCHAR(20),
    remote_ip VARCHAR(15),
    local_ip VARCHAR(15),
    remote_network VARCHAR(50),
    local_network VARCHAR(50),
    username VARCHAR(50),
    password VARCHAR(100),
    secret VARCHAR(100),
    status VARCHAR(20) DEFAULT 'disconnected',
    last_connected TIMESTAMP,
    bandwidth_limit BIGINT,
    description VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- NETWORK MODULE: IP Pools
-- =====================================================
CREATE TABLE IF NOT EXISTS ip_pools (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    pool_name VARCHAR(50) UNIQUE NOT NULL,
    start_ip VARCHAR(15) NOT NULL,
    end_ip VARCHAR(15) NOT NULL,
    gateway VARCHAR(15),
    dns_primary VARCHAR(15),
    dns_secondary VARCHAR(15),
    vlan INT,
    network_address VARCHAR(15),
    cidr_prefix INT,
    description VARCHAR(500),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- NETWORK MODULE: ACS Devices
-- =====================================================
CREATE TABLE IF NOT EXISTS acs_settings (
    id BIGINT PRIMARY KEY,
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
    olt_port INT,
    onu_id INT,
    optical_rx_power DOUBLE PRECISION,
    optical_tx_power DOUBLE PRECISION,
    optical_temperature DOUBLE PRECISION,
    optical_voltage DOUBLE PRECISION,
    wifi_name VARCHAR(100),
    status VARCHAR(20) DEFAULT 'offline',
    connection_type VARCHAR(20),
    distance INT,
    last_acs_request TIMESTAMP,
    last_inform TIMESTAMP,
    last_synced_at TIMESTAMP,
    is_provisioned BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- INVENTORY MODULE
-- =====================================================
CREATE TABLE IF NOT EXISTS inventory_items (
    id BIGSERIAL PRIMARY KEY,
    item_code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(50),
    description VARCHAR(500),
    unit VARCHAR(20),
    min_stock INT,
    current_stock INT DEFAULT 0,
    price DECIMAL(12,2),
    supplier_id BIGINT,
    location VARCHAR(200),
    warehouse VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS inventory_transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_code VARCHAR(50) UNIQUE,
    item_id BIGINT NOT NULL REFERENCES inventory_items(id),
    type VARCHAR(10) NOT NULL,
    quantity INT NOT NULL,
    reference VARCHAR(100),
    notes VARCHAR(500),
    technician_id BIGINT REFERENCES technicians(id),
    created_by_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE inventory_transactions
    ADD COLUMN IF NOT EXISTS transaction_code VARCHAR(50);

ALTER TABLE inventory_transactions
    ADD COLUMN IF NOT EXISTS technician_id BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_inventory_transactions_code
    ON inventory_transactions(transaction_code);

-- =====================================================
-- CRM MODULE
-- =====================================================
CREATE TABLE IF NOT EXISTS crm_contacts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    company VARCHAR(200),
    address VARCHAR(500),
    source VARCHAR(50),
    status VARCHAR(20) DEFAULT 'lead',
    assigned_to_id BIGINT REFERENCES users(id),
    notes TEXT,
    next_follow_up TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- MONITORING MODULE: Network Devices
-- =====================================================
CREATE TABLE IF NOT EXISTS network_devices (
    id BIGSERIAL PRIMARY KEY,
    device_name VARCHAR(100) NOT NULL,
    device_type VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    location VARCHAR(100),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    vendor VARCHAR(50),
    model VARCHAR(50),
    firmware_version VARCHAR(50),
    serial_number VARCHAR(100),
    mac_address VARCHAR(17),
    status VARCHAR(20) DEFAULT 'offline',
    uptime_seconds BIGINT,
    last_ping_time TIMESTAMP,
    last_snmp_time TIMESTAMP,
    ping_interval INTEGER DEFAULT 30,
    snmp_interval INTEGER DEFAULT 60,
    snmp_community VARCHAR(100),
    snmp_version VARCHAR(10) DEFAULT 'v2c',
    api_port INTEGER,
    api_username VARCHAR(100),
    cpu_warning_threshold INTEGER DEFAULT 70,
    cpu_critical_threshold INTEGER DEFAULT 90,
    memory_warning_threshold INTEGER DEFAULT 80,
    memory_critical_threshold INTEGER DEFAULT 95,
    temperature_warning_threshold INTEGER DEFAULT 45,
    temperature_critical_threshold INTEGER DEFAULT 60,
    parent_device_id BIGINT,
    notes TEXT,
    is_monitored BOOLEAN DEFAULT true,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add foreign key for parent device
ALTER TABLE network_devices 
ADD CONSTRAINT fk_network_devices_parent 
FOREIGN KEY (parent_device_id) REFERENCES network_devices(id) ON DELETE SET NULL;

-- =====================================================
-- MONITORING MODULE: Device Metrics
-- =====================================================
CREATE TABLE IF NOT EXISTS device_metrics (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL REFERENCES network_devices(id) ON DELETE CASCADE,
    cpu_usage DECIMAL(5, 2),
    memory_usage DECIMAL(5, 2),
    memory_used BIGINT,
    memory_total BIGINT,
    disk_usage DECIMAL(5, 2),
    disk_used BIGINT,
    disk_total BIGINT,
    temperature DECIMAL(5, 2),
    traffic_rx_bytes BIGINT,
    traffic_tx_bytes BIGINT,
    traffic_rx_bps BIGINT,
    traffic_tx_bps BIGINT,
    packets_rx BIGINT,
    packets_tx BIGINT,
    errors_rx BIGINT,
    errors_tx BIGINT,
    interface_name VARCHAR(50),
    interface_status VARCHAR(20),
    interface_speed INTEGER,
    interface_duplex VARCHAR(20),
    onu_total INTEGER,
    onu_online INTEGER,
    onu_offline INTEGER,
    optical_rx_power DECIMAL(6, 2),
    optical_tx_power DECIMAL(6, 2),
    pon_port_status VARCHAR(20),
    active_sessions INTEGER,
    active_pppoe INTEGER,
    active_hotspot INTEGER,
    firewall_connections INTEGER,
    latency_ms DECIMAL(10, 2),
    jitter_ms DECIMAL(10, 2),
    packet_loss DECIMAL(5, 2),
    uptime_seconds BIGINT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_device_metrics_device_time ON device_metrics(device_id, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_device_metrics_timestamp ON device_metrics(timestamp DESC);

-- =====================================================
-- MONITORING MODULE: Device Interfaces
-- =====================================================
CREATE TABLE IF NOT EXISTS device_interfaces (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT NOT NULL REFERENCES network_devices(id) ON DELETE CASCADE,
    interface_name VARCHAR(50) NOT NULL,
    interface_type VARCHAR(20),
    description VARCHAR(200),
    mac_address VARCHAR(17),
    ip_address VARCHAR(45),
    status VARCHAR(20) DEFAULT 'unknown',
    speed INTEGER,
    duplex VARCHAR(20),
    vlan_id INTEGER,
    bytes_rx BIGINT DEFAULT 0,
    bytes_tx BIGINT DEFAULT 0,
    packets_rx BIGINT DEFAULT 0,
    packets_tx BIGINT DEFAULT 0,
    errors_rx BIGINT DEFAULT 0,
    errors_tx BIGINT DEFAULT 0,
    rx_bps BIGINT DEFAULT 0,
    tx_bps BIGINT DEFAULT 0,
    pon_port_number INTEGER,
    onu_count INTEGER DEFAULT 0,
    last_checked TIMESTAMP,
    is_monitored BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(device_id, interface_name)
);

CREATE INDEX IF NOT EXISTS idx_device_interfaces_device ON device_interfaces(device_id);

-- =====================================================
-- MONITORING MODULE: Network Alerts
-- =====================================================
CREATE TABLE IF NOT EXISTS network_alerts (
    id BIGSERIAL PRIMARY KEY,
    device_id BIGINT,
    device_name VARCHAR(100),
    device_type VARCHAR(50),
    alert_type VARCHAR(50),
    severity VARCHAR(20),
    message VARCHAR(500),
    value DOUBLE PRECISION,
    threshold DOUBLE PRECISION,
    is_acknowledged BOOLEAN DEFAULT FALSE,
    acknowledged_by_id BIGINT REFERENCES users(id),
    acknowledged_at TIMESTAMP,
    acknowledged_notes VARCHAR(500),
    is_resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- MONITORING MODULE: Customer Sessions
-- =====================================================
CREATE TABLE IF NOT EXISTS customer_sessions (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT,
    device_id BIGINT REFERENCES network_devices(id) ON DELETE SET NULL,
    username VARCHAR(100),
    ip_address VARCHAR(45),
    mac_address VARCHAR(17),
    session_type VARCHAR(20),
    session_id VARCHAR(100),
    service_name VARCHAR(100),
    bytes_in BIGINT DEFAULT 0,
    bytes_out BIGINT DEFAULT 0,
    packets_in BIGINT DEFAULT 0,
    packets_out BIGINT DEFAULT 0,
    connect_time TIMESTAMP,
    uptime_seconds BIGINT,
    nas_ip VARCHAR(45),
    status VARCHAR(20) DEFAULT 'active',
    last_activity TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_customer_sessions_device ON customer_sessions(device_id);
CREATE INDEX IF NOT EXISTS idx_customer_sessions_status ON customer_sessions(status);
CREATE INDEX IF NOT EXISTS idx_customer_sessions_username ON customer_sessions(username);

-- =====================================================
-- MONITORING MODULE: Alert Rules
-- =====================================================
CREATE TABLE IF NOT EXISTS alert_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL,
    description TEXT,
    device_type VARCHAR(50),
    metric_type VARCHAR(50),
    condition_type VARCHAR(50),
    threshold_value DECIMAL(10, 2),
    threshold_value2 DECIMAL(10, 2),
    duration_seconds INTEGER DEFAULT 0,
    severity VARCHAR(20),
    notification_channels JSONB,
    auto_resolve BOOLEAN DEFAULT false,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- COMPANY & SYSTEM MODULE
-- =====================================================
CREATE TABLE IF NOT EXISTS company_profiles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
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
    facebook VARCHAR(100),
    instagram VARCHAR(100),
    twitter VARCHAR(100),
    whatsapp VARCHAR(20),
    support_email VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS automation_rules (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    trigger_type VARCHAR(50),
    trigger_config TEXT,
    conditions TEXT,
    actions TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_by_id BIGINT REFERENCES users(id),
    last_executed TIMESTAMP,
    execution_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =====================================================
-- INITIAL DATA: Service Types
-- =====================================================
INSERT INTO service_types (name, description) VALUES 
('PPPoE', 'Point-to-Point Protocol over Ethernet'),
('Hotspot', 'Mikrotik Hotspot authentication'),
('Static IP', 'Dedicated static IP address'),
('GPON', 'Gigabit Passive Optical Network'),
('Dedicated BW', 'Dedicated Bandwidth - Fixed speed'),
('Up To BW', 'Up To Bandwidth - Shared speed'),
('Broadcast', 'Broadcast service for multicast'),
('Metro', 'Metro Ethernet service')
ON CONFLICT (name) DO NOTHING;

-- =====================================================
-- INITIAL DATA: Regions
-- =====================================================
INSERT INTO regions (name, code) VALUES 
('Jakarta Pusat', 'JKT-PUSAT'),
('Jakarta Utara', 'JKT-UTARA'),
('Jakarta Selatan', 'JKT-SELATAN'),
('Jakarta Barat', 'JKT-BARAT'),
('Jakarta Timur', 'JKT-TIMUR'),
('Bogor', 'BGR'),
('Depok', 'DPK'),
('Tangerang', 'TNG'),
('Bekasi', 'BKS')
ON CONFLICT (code) DO NOTHING;

-- =====================================================
-- INITIAL DATA: Internet Packages
-- =====================================================
INSERT INTO internet_packages (name, speed_down, speed_up, price, description, is_active) VALUES 
('Home 10 Mbps', 10, 5, 150000, 'Paket Rumah 10 Mbps - Unlimited', true),
('Home 20 Mbps', 20, 10, 200000, 'Paket Rumah 20 Mbps - Unlimited', true),
('Home 30 Mbps', 30, 15, 250000, 'Paket Rumah 30 Mbps - Unlimited', true),
('Business 50 Mbps', 50, 25, 500000, 'Paket Bisnis 50 Mbps - Unlimited', true),
('Business 100 Mbps', 100, 50, 750000, 'Paket Bisnis 100 Mbps - Unlimited', true),
('Gaming 100 Mbps', 100, 100, 350000, 'Paket Gaming 100 Mbps - Low Latency', true),
('Gaming 200 Mbps', 200, 200, 500000, 'Paket Gaming 200 Mbps - Ultra Low Latency', true)
ON CONFLICT DO NOTHING;

-- =====================================================
-- INITIAL DATA: Roles
-- =====================================================
INSERT INTO roles (name, description, permissions_level) VALUES 
('ROLE_SUPER_ADMIN', 'Super Administrator - Full CRUD Access', 'FULL'),
('ROLE_ADMIN', 'Administrator - Create, Read, Update, Delete', 'WRITE'),
('ROLE_SIDE_ADMIN', 'Side Admin - Create and Read only', 'READ'),
('ROLE_TECHNICIAN', 'Technician - Limited Access', 'READ'),
('ROLE_FINANCE', 'Finance - Billing Access', 'READ')
ON CONFLICT (name) DO NOTHING;

-- =====================================================
-- INITIAL DATA: Superadmin User (NMX-001)
-- =====================================================
-- Password: netmaster (BCrypt: $2y$10$ktID1extQsVnzAtmpHUbC.7WYNDoYh1eiI.CnqGt4iV6WY75d4xLa)
INSERT INTO users (employee_id, full_name, username, password, email, phone, is_active) VALUES 
('NMX-001', 'superadmin', 'netmaster', '$2y$10$ktID1extQsVnzAtmpHUbC.7WYNDoYh1eiI.CnqGt4iV6WY75d4xLa', 'netmaster.idn@gmail.com', '08158077992', true)
ON CONFLICT (username) DO NOTHING;

-- Assign SUPER_ADMIN role to superadmin user
INSERT INTO user_roles (user_id, role_id, assigned_by) 
SELECT u.id, r.id, 'SYSTEM'
FROM users u, roles r
WHERE u.username = 'netmaster' AND r.name = 'ROLE_SUPER_ADMIN'
ON CONFLICT DO NOTHING;

-- =====================================================
-- INITIAL DATA: Network Devices
-- =====================================================
-- Mikrotik, OLT, dan IP Pool dikelola penuh dari aplikasi.
-- Biarkan tabel kosong saat inisialisasi agar tidak ada mock data bawaan.

-- =====================================================
-- INITIAL DATA: Technicians
-- =====================================================
-- Tidak ada seed default untuk teknisi.
-- Data teknisi harus berasal dari input pengguna pada tabel technicians.

-- =====================================================
-- INITIAL DATA: Inventory Items
-- =====================================================
INSERT INTO inventory_items (item_code, name, category, description, unit, min_stock, current_stock, price, location) VALUES
('MIK-202603-0001', 'Mikrotik RB750Gr3', 'Mikrotik', 'Router Mikrotik untuk distribusi pelanggan', 'pcs', 3, 12, 850000, 'Gudang Pusat'),
('OLT-202603-0001', 'Huawei MA5800 X7', 'Olt', 'Perangkat OLT GPON untuk sentral POP', 'unit', 1, 2, 45000000, 'Gudang Pusat'),
('ODP-202603-0001', 'ODP 16 Port Outdoor', 'ODP', 'ODP distribusi jaringan FTTH 16 port', 'pcs', 5, 24, 425000, 'Gudang Cabang'),
('SPF-202603-0001', 'Splitter Pasif 1:8', 'Splitter Pasif', 'PLC splitter pasif 1 banding 8', 'pcs', 10, 60, 72000, 'Gudang Pusat'),
('SRA-202603-0001', 'Splitter Rasio 70:30', 'Splitter Rasio', 'Splitter rasio untuk distribusi optik', 'pcs', 8, 40, 81000, 'Gudang Pusat'),
('ONT-202603-0001', 'ZTE F609', 'ONT', 'Perangkat ONT untuk pelanggan GPON', 'pcs', 10, 35, 315000, 'Gudang Cabang'),
('STB-202603-0001', 'STB Android Fiber TV', 'STB', 'Set top box untuk layanan IPTV', 'pcs', 4, 18, 425000, 'Gudang Pusat'),
('PTC-202603-0001', 'Patchcord SC/UPC 3 Meter', 'Patchcord', 'Patchcord fiber optic SC UPC 3 meter', 'pcs', 12, 75, 18000, 'Gudang Pusat'),
('SLV-202603-0001', 'Sleeve Protecion 60mm', 'Sleeve Protecion', 'Sleeve pelindung sambungan fiber optic', 'pcs', 50, 240, 2500, 'Gudang Cabang'),
('INV-202603-0001', 'Cable Ties 20cm', 'Lainnya', 'Aksesoris pendukung instalasi jaringan', 'pack', 6, 28, 12000, 'Gudang Pusat')
ON CONFLICT (item_code) DO NOTHING;

-- =====================================================
-- INITIAL DATA: CRM Contacts
-- =====================================================
INSERT INTO crm_contacts (name, email, phone, company, source, status) VALUES
('Ahmad Susanto', 'ahmad@email.com', '081234567890', 'PT Maju Jaya', 'website', 'lead'),
('Budi Santoso', 'budi@company.co.id', '081234567891', 'CV Berkah', 'referral', 'prospect'),
('Citra Dewi', 'citra@startup.id', '081234567892', 'Startup Digital', 'advertisement', 'customer')
ON CONFLICT DO NOTHING;

-- =====================================================
-- INITIAL DATA: Network Alerts
-- =====================================================
INSERT INTO network_alerts (device_name, device_type, alert_type, severity, message, value, threshold) VALUES
('Mikrotik-Router-02', 'mikrotik', 'device_down', 'critical', 'Device tidak dapat diakses', 0, 1),
('OLT-Surabaya-01', 'olt', 'high_cpu', 'warning', 'CPU usage tinggi', 85, 80)
ON CONFLICT DO NOTHING;

-- =====================================================
-- INITIAL DATA: Company Profile
-- =====================================================
INSERT INTO company_profiles (name, address, phone, email, website, tagline) VALUES
('NetMaster ISP', 'Jl. Merdeka No. 123, Jakarta', '021-1234567', 'info@netmaster.id', 'www.netmaster.id', 'Solusi Internet Terdepan')
ON CONFLICT DO NOTHING;

-- =====================================================
-- INITIAL DATA: Automation Rules
-- =====================================================
INSERT INTO automation_rules (name, description, trigger_type, is_active) VALUES
('Auto Backup Mikrotik', 'Backup konfigurasi Mikrotik setiap hari', 'scheduled', TRUE),
('Auto Suspend Overdue', 'Suspennd pelanggan dengan tagihan overdue', 'scheduled', TRUE),
('Alert Device Down', 'Kirim notifikasi jika device offline', 'event', TRUE)
ON CONFLICT DO NOTHING;

-- =====================================================
-- INITIAL DATA: Alert Rules
-- =====================================================
INSERT INTO alert_rules (rule_name, description, metric_type, condition_type, threshold_value, severity, is_active) VALUES
    ('High CPU Warning', 'Alert when CPU usage exceeds 70%', 'cpu', 'greater_than', 70, 'WARNING', true),
    ('High CPU Critical', 'Alert when CPU usage exceeds 90%', 'cpu', 'greater_than', 90, 'CRITICAL', true),
    ('High Memory Warning', 'Alert when memory usage exceeds 80%', 'memory', 'greater_than', 80, 'WARNING', true),
    ('High Memory Critical', 'Alert when memory usage exceeds 95%', 'memory', 'greater_than', 95, 'CRITICAL', true),
    ('Device Offline', 'Alert when device goes offline', 'ping', 'equals', 0, 'CRITICAL', true),
    ('High Temperature', 'Alert when device temperature exceeds 50C', 'temperature', 'greater_than', 50, 'WARNING', true),
    ('Critical Temperature', 'Alert when device temperature exceeds 60C', 'temperature', 'greater_than', 60, 'CRITICAL', true),
    ('High Packet Loss', 'Alert when packet loss exceeds 1%', 'packet_loss', 'greater_than', 1, 'WARNING', true),
    ('OLT ONU Offline', 'Alert when OLT has offline ONUs', 'onu_offline', 'greater_than', 0, 'WARNING', true),
    ('Optical Power Low', 'Alert when optical power is below -28dBm', 'optical_rx', 'less_than', -28, 'CRITICAL', true)
ON CONFLICT DO NOTHING;

-- =====================================================
-- VIEWS
-- =====================================================

-- View: Customer with Service
CREATE OR REPLACE VIEW customer_with_service AS
SELECT 
    c.id,
    c.customer_code,
    c.full_name,
    c.email,
    c.phone,
    c.ktp_number,
    c.installation_address,
    c.status AS customer_status,
    c.registration_date,
    p.name AS package_name,
    p.speed_down,
    p.speed_up,
    p.price AS package_price,
    st.name AS service_type,
    cs.pppoe_username,
    cs.ip_address,
    cs.ont_serial,
    cs.monthly_fee,
    cs.installation_fee,
    cs.status AS service_status,
    odp.name AS odp_name,
    odp.code AS odp_code,
    odc.name AS odc_name,
    s.name AS server_name,
    r.name AS region_name,
    c.latitude,
    c.longitude
FROM customers c
LEFT JOIN customer_services cs ON c.id = cs.customer_id
LEFT JOIN internet_packages p ON cs.package_id = p.id
LEFT JOIN service_types st ON cs.service_type_id = st.id
LEFT JOIN odps odp ON cs.odp_id = odp.id
LEFT JOIN odcs odc ON odp.odc_id = odc.id
LEFT JOIN servers s ON odc.server_id = s.id
LEFT JOIN regions r ON c.region_id = r.id;

-- View: ODP Availability
CREATE OR REPLACE VIEW odp_availability AS
SELECT 
    o.id,
    o.name,
    o.code,
    o.capacity,
    o.used_port,
    (o.capacity - o.used_port) AS available_port,
    ROUND((o.used_port::numeric / o.capacity::numeric) * 100, 1) AS usage_percentage,
    o.is_active,
    o.location,
    odc.name AS odc_name,
    odc.code AS odc_code,
    s.name AS server_name,
    r.name AS region_name
FROM odps o
LEFT JOIN odcs odc ON o.odc_id = odc.id
LEFT JOIN servers s ON odc.server_id = s.id
LEFT JOIN regions r ON s.region_id = r.id
ORDER BY (o.capacity - o.used_port) ASC;

-- View: User with Roles
CREATE OR REPLACE VIEW user_with_roles AS
SELECT 
    u.id,
    u.employee_id,
    u.full_name,
    u.username,
    u.email,
    u.phone,
    u.is_active,
    u.created_at,
    STRING_AGG(r.name, ', ') AS role_names,
    STRING_AGG(r.permissions_level, ', ') AS permissions_levels
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
LEFT JOIN roles r ON ur.role_id = r.id
GROUP BY u.id, u.employee_id, u.full_name, u.username, u.email, u.phone, u.is_active, u.created_at;

-- =====================================================
-- FUNCTIONS
-- =====================================================

-- Function: Generate Customer Code
CREATE OR REPLACE FUNCTION generate_customer_code()
RETURNS TRIGGER AS $$
DECLARE
    prefix TEXT;
    today TEXT;
    seq_num INT;
    new_code TEXT;
BEGIN
    prefix := 'CSM';
    today := TO_CHAR(CURRENT_DATE, 'YYYYMMDD');
    
    SELECT COALESCE(MAX(
        CAST(SUBSTRING(customer_code FROM 10 FOR 3) AS INT)
    ), 0) + 1 INTO seq_num
    FROM customers
    WHERE customer_code LIKE prefix || '-' || today || '-%';
    
    new_code := prefix || '-' || today || '-' || LPAD(seq_num::TEXT, 3, '0');
    NEW.customer_code := new_code;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function: Generate ODC Code
CREATE OR REPLACE FUNCTION generate_odc_code()
RETURNS TRIGGER AS $$
DECLARE
    period TEXT;
    seq_num INT;
BEGIN
    IF NEW.code IS NOT NULL AND BTRIM(NEW.code) <> '' THEN
        RETURN NEW;
    END IF;

    period := TO_CHAR(COALESCE(NEW.created_at, CURRENT_TIMESTAMP), 'YYYYMM');

    SELECT COALESCE(MAX(CAST(regexp_replace(code, '^ODC-' || period || '-', '') AS INT)), 0) + 1
    INTO seq_num
    FROM odcs
    WHERE code LIKE 'ODC-' || period || '-%';

    NEW.code := 'ODC-' || period || '-' || LPAD(seq_num::TEXT, 4, '0');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function: Generate ODP Code
CREATE OR REPLACE FUNCTION generate_odp_code()
RETURNS TRIGGER AS $$
DECLARE
    prefix TEXT;
    period TEXT;
    seq_num INT;
BEGIN
    IF NEW.code IS NOT NULL AND BTRIM(NEW.code) <> '' THEN
        RETURN NEW;
    END IF;

    prefix := CASE WHEN UPPER(COALESCE(NEW.node_type, 'ODP')) = 'ODC' THEN 'ODC' ELSE 'ODP' END;
    period := TO_CHAR(COALESCE(NEW.created_at, CURRENT_TIMESTAMP), 'YYYYMM');

    SELECT COALESCE(MAX(CAST(regexp_replace(code, '^' || prefix || '-' || period || '-', '') AS INT)), 0) + 1
    INTO seq_num
    FROM odps
    WHERE code LIKE prefix || '-' || period || '-%';

    NEW.node_type := UPPER(COALESCE(NEW.node_type, 'ODP'));
    NEW.code := prefix || '-' || period || '-' || LPAD(seq_num::TEXT, 4, '0');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function: Generate Ticket Number
CREATE OR REPLACE FUNCTION generate_ticket_number()
RETURNS TRIGGER AS $$
DECLARE
    prefix TEXT;
    today TEXT;
    seq_num INT;
    new_number TEXT;
BEGIN
    prefix := 'TKT';
    today := TO_CHAR(CURRENT_DATE, 'YYYYMMDD');
    
    SELECT COALESCE(MAX(
        CAST(SUBSTRING(ticket_number FROM 10 FOR 4) AS INT)
    ), 0) + 1 INTO seq_num
    FROM tickets
    WHERE ticket_number LIKE prefix || '-' || today || '-%';
    
    new_number := prefix || '-' || today || '-' || LPAD(seq_num::TEXT, 4, '0');
    NEW.ticket_number := new_number;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Function: Update ODP Port Usage
CREATE OR REPLACE FUNCTION update_odp_port_usage()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' AND NEW.odp_id IS NOT NULL THEN
        UPDATE odps SET used_port = used_port + 1 WHERE id = NEW.odp_id;
    ELSIF TG_OP = 'DELETE' AND OLD.odp_id IS NOT NULL THEN
        UPDATE odps SET used_port = used_port - 1 WHERE id = OLD.odp_id AND used_port > 0;
    ELSIF TG_OP = 'UPDATE' AND OLD.odp_id IS NOT NULL AND NEW.odp_id IS NOT NULL AND OLD.odp_id != NEW.odp_id THEN
        UPDATE odps SET used_port = used_port - 1 WHERE id = OLD.odp_id AND used_port > 0;
        UPDATE odps SET used_port = used_port + 1 WHERE id = NEW.odp_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- TRIGGERS
-- =====================================================

-- Trigger: Auto Generate Customer Code
CREATE TRIGGER trigger_generate_customer_code
    BEFORE INSERT ON customers
    FOR EACH ROW
    WHEN (NEW.customer_code IS NULL)
    EXECUTE FUNCTION generate_customer_code();

-- Trigger: Auto Generate ODC Code
CREATE TRIGGER trigger_generate_odc_code
    BEFORE INSERT ON odcs
    FOR EACH ROW
    WHEN (NEW.code IS NULL)
    EXECUTE FUNCTION generate_odc_code();

-- Trigger: Auto Generate ODP Code
CREATE TRIGGER trigger_generate_odp_code
    BEFORE INSERT ON odps
    FOR EACH ROW
    WHEN (NEW.code IS NULL)
    EXECUTE FUNCTION generate_odp_code();

-- Trigger: Auto Generate Ticket Number
CREATE TRIGGER trigger_generate_ticket_number
    BEFORE INSERT ON tickets
    FOR EACH ROW
    WHEN (NEW.ticket_number IS NULL)
    EXECUTE FUNCTION generate_ticket_number();

-- Trigger: Update ODP Port Usage
CREATE TRIGGER trigger_update_odp_port
    AFTER INSERT OR UPDATE OR DELETE ON customer_services
    FOR EACH ROW
    EXECUTE FUNCTION update_odp_port_usage();

-- =====================================================
-- COMMENTS
-- =====================================================
COMMENT ON TABLE service_types IS 'Tipe layanan internet (PPPoE, Hotspot, Static IP, GPON)';
COMMENT ON TABLE regions IS 'Wilayah/kota untuk region mapping';
COMMENT ON TABLE internet_packages IS 'Paket internet dengan speed dan harga';
COMMENT ON TABLE servers IS 'Server/OLT utama';
COMMENT ON TABLE odcs IS 'Optical Distribution Cabinet - kotak distribusi fiber';
COMMENT ON TABLE odps IS 'Optical Distribution Point - titik distribusi ke pelanggan';
COMMENT ON TABLE customers IS 'Data lengkap pelanggan';
COMMENT ON TABLE customer_services IS 'Layanan pelanggan (paket, ODP, ONT, PPPoE)';
COMMENT ON TABLE network_topology_links IS 'Relasi garis topologi server ke ODC ke ODP ke ONT';
COMMENT ON TABLE ont_action_logs IS 'Log quick action ONT seperti restart dan update WiFi';
COMMENT ON TABLE technicians IS 'Data teknisi/installer';
COMMENT ON TABLE users IS 'Data pengguna sistem';
COMMENT ON TABLE roles IS 'Peran pengguna dengan level permissions';
COMMENT ON TABLE user_roles IS 'Relasi many-to-many antara user dan role';
COMMENT ON TABLE invoices IS 'Tagihan pelanggan';
COMMENT ON TABLE payments IS 'Transaksi pembayaran pelanggan';
COMMENT ON TABLE tickets IS 'Tiket dukungan pelanggan';
COMMENT ON TABLE mikrotik_devices IS 'Device Mikrotik Router';
COMMENT ON TABLE olt_devices IS 'Optical Line Terminal devices';
COMMENT ON TABLE vpn_connections IS 'VPN connections';
COMMENT ON TABLE ip_pools IS 'IP address pools';
COMMENT ON TABLE acs_devices IS 'ACS (Auto Configuration Server) devices';
COMMENT ON TABLE inventory_items IS 'Inventory/stok barang';
COMMENT ON TABLE inventory_transactions IS 'Riwayat transaksi inventory';
COMMENT ON TABLE crm_contacts IS 'CRM contacts/leads';
COMMENT ON TABLE network_devices IS 'Unified network device inventory for monitoring';
COMMENT ON TABLE device_metrics IS 'Time-series metrics from network devices';
COMMENT ON TABLE device_interfaces IS 'Network interfaces per device';
COMMENT ON TABLE network_alerts IS 'Network monitoring alerts';
COMMENT ON TABLE customer_sessions IS 'Active customer connections (PPPoE, Hotspot)';

ALTER TABLE customers ADD COLUMN IF NOT EXISTS whatsapp_number VARCHAR(30);
ALTER TABLE customers ADD COLUMN IF NOT EXISTS billing_due_day INT;
ALTER TABLE customers ADD COLUMN IF NOT EXISTS pppoe_status VARCHAR(20) DEFAULT 'unknown';
ALTER TABLE customers ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE invoices ADD COLUMN IF NOT EXISTS sent_at TIMESTAMP;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS send_method VARCHAR(30);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS whatsapp_status VARCHAR(30);
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS last_reminder_at TIMESTAMP;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP;

ALTER TABLE payments ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'paid';
ALTER TABLE payments ADD COLUMN IF NOT EXISTS receipt_sent_at TIMESTAMP;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS receipt_send_method VARCHAR(30);

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
COMMENT ON TABLE alert_rules IS 'Configurable alert detection rules';
COMMENT ON TABLE company_profiles IS 'Company profile information';
COMMENT ON TABLE automation_rules IS 'Automation rules';

-- =====================================================
-- END OF DATABASE SCHEMA
-- =====================================================




