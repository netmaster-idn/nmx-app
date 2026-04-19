ALTER TABLE IF EXISTS customer_services
    ADD COLUMN IF NOT EXISTS ont_redaman DECIMAL(6,2);

ALTER TABLE IF EXISTS customer_services
    ADD COLUMN IF NOT EXISTS wifi_name VARCHAR(100);

ALTER TABLE IF EXISTS customer_services
    ADD COLUMN IF NOT EXISTS wifi_password VARCHAR(100);

ALTER TABLE IF EXISTS customer_services
    ADD COLUMN IF NOT EXISTS ont_standby_since TIMESTAMP;

ALTER TABLE IF EXISTS customer_services
    ADD COLUMN IF NOT EXISTS last_restart_requested_at TIMESTAMP;

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

CREATE UNIQUE INDEX IF NOT EXISTS idx_network_topology_links_pair
    ON network_topology_links (from_node_type, from_node_id, to_node_type, to_node_id);

CREATE TABLE IF NOT EXISTS ont_action_logs (
    id BIGSERIAL PRIMARY KEY,
    customer_service_id BIGINT NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    payload TEXT,
    requested_by VARCHAR(100),
    status VARCHAR(30) DEFAULT 'queued',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ont_action_logs_service
    ON ont_action_logs (customer_service_id);
