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
public class NetworkSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS vpn_ip_address VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ALTER COLUMN vpn_ip_address TYPE VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS winbox_ip_address VARCHAR(45)");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS api_ip_address VARCHAR(45)");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS monitoring_target VARCHAR(20)");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ALTER COLUMN ip_address DROP NOT NULL");
        apply("ALTER TABLE IF EXISTS servers ADD COLUMN IF NOT EXISTS mikrotik_id BIGINT");

        apply("ALTER TABLE IF EXISTS olt_devices ADD COLUMN IF NOT EXISTS vpn_ip_address VARCHAR(45)");
        apply("ALTER TABLE IF EXISTS olt_devices ADD COLUMN IF NOT EXISTS username VARCHAR(50)");
        apply("ALTER TABLE IF EXISTS olt_devices ADD COLUMN IF NOT EXISTS password VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS olt_devices ALTER COLUMN ip_address DROP NOT NULL");

        apply("ALTER TABLE IF EXISTS network_devices ADD COLUMN IF NOT EXISTS source_type VARCHAR(50)");
        apply("ALTER TABLE IF EXISTS network_devices ADD COLUMN IF NOT EXISTS source_id BIGINT");
        apply("CREATE INDEX IF NOT EXISTS idx_network_devices_source ON network_devices (source_type, source_id)");

        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS device_id BIGINT");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS device_name VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS device_type VARCHAR(50)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS device_ip VARCHAR(45)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS location VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS alert_type VARCHAR(50)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS metric_type VARCHAR(50)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS metric_value DOUBLE PRECISION");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS threshold DOUBLE PRECISION");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS duration_minutes INTEGER");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS severity VARCHAR(20)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS status VARCHAR(20)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS alert_id VARCHAR(50)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS source VARCHAR(20)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS message VARCHAR(500)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS assigned_engineer VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS affected_customers INTEGER");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS affected_service VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS sla_impact VARCHAR(20)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS is_acknowledged BOOLEAN DEFAULT FALSE");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS acknowledged_by BIGINT");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS acknowledged_at TIMESTAMP");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS acknowledged_notes VARCHAR(500)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS is_resolved BOOLEAN DEFAULT FALSE");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS resolved_notes VARCHAR(500)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS is_closed BOOLEAN DEFAULT FALSE");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS investigation_notes VARCHAR(1000)");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS occurrence_count INTEGER DEFAULT 1");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS created_at TIMESTAMP");
        apply("ALTER TABLE IF EXISTS network_alerts ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP");
        apply("UPDATE network_alerts SET status = 'active' WHERE status IS NULL");
        apply("UPDATE network_alerts SET severity = 'warning' WHERE severity IS NULL");
        apply("UPDATE network_alerts SET source = 'SYSTEM' WHERE source IS NULL");
        apply("UPDATE network_alerts SET created_at = NOW() WHERE created_at IS NULL");
        apply("UPDATE network_alerts SET updated_at = NOW() WHERE updated_at IS NULL");
        apply("UPDATE network_alerts SET alert_id = CONCAT('ALT-', TO_CHAR(COALESCE(created_at, NOW()), 'YYYYMMDD'), '-', LPAD(id::text, 3, '0')) WHERE alert_id IS NULL");
        apply("CREATE UNIQUE INDEX IF NOT EXISTS idx_network_alerts_alert_id ON network_alerts (alert_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_network_alerts_status ON network_alerts (status)");
        apply("CREATE INDEX IF NOT EXISTS idx_network_alerts_severity ON network_alerts (severity)");
        apply("CREATE INDEX IF NOT EXISTS idx_network_alerts_created_at ON network_alerts (created_at)");

        apply("""
                CREATE TABLE IF NOT EXISTS devices_status_cache (
                    id BIGSERIAL PRIMARY KEY,
                    device_id BIGINT NOT NULL UNIQUE,
                    device_name VARCHAR(100) NOT NULL,
                    ip_address VARCHAR(45),
                    role_name VARCHAR(50),
                    location VARCHAR(100),
                    status VARCHAR(20),
                    maintenance_status VARCHAR(20),
                    warning_status VARCHAR(20),
                    cpu_usage NUMERIC(10,2),
                    memory_usage NUMERIC(10,2),
                    uptime_seconds BIGINT,
                    latency_ms NUMERIC(10,2),
                    packet_loss NUMERIC(10,2),
                    health_score INTEGER,
                    freshness_status VARCHAR(20),
                    alert_count INTEGER DEFAULT 0,
                    last_seen TIMESTAMP,
                    last_ping_success_at TIMESTAMP,
                    last_api_success_at TIMESTAMP,
                    last_sync_at TIMESTAMP,
                    sync_source VARCHAR(30),
                    maintenance_reason VARCHAR(500),
                    maintenance_starts_at TIMESTAMP,
                    maintenance_ends_at TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT NOW()
                )
                """);
        apply("""
                CREATE TABLE IF NOT EXISTS device_health_snapshots (
                    id BIGSERIAL PRIMARY KEY,
                    device_id BIGINT NOT NULL,
                    status VARCHAR(20),
                    cpu_usage NUMERIC(10,2),
                    memory_usage NUMERIC(10,2),
                    latency_ms NUMERIC(10,2),
                    packet_loss NUMERIC(10,2),
                    health_score INTEGER,
                    freshness_status VARCHAR(20),
                    captured_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """);
        apply("""
                CREATE TABLE IF NOT EXISTS network_device_sync_status (
                    id BIGSERIAL PRIMARY KEY,
                    device_id BIGINT NOT NULL,
                    module_name VARCHAR(50) NOT NULL,
                    last_attempt_at TIMESTAMP,
                    last_success_at TIMESTAMP,
                    last_error VARCHAR(500),
                    fail_count INTEGER DEFAULT 0,
                    stale_after_seconds INTEGER,
                    breaker_until TIMESTAMP,
                    status VARCHAR(20) DEFAULT 'idle',
                    last_duration_ms BIGINT,
                    last_item_count INTEGER
                )
                """);
        apply("""
                CREATE TABLE IF NOT EXISTS device_maintenance_windows (
                    id BIGSERIAL PRIMARY KEY,
                    device_id BIGINT NOT NULL,
                    starts_at TIMESTAMP NOT NULL,
                    ends_at TIMESTAMP NOT NULL,
                    reason VARCHAR(500),
                    created_by VARCHAR(100),
                    is_active BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT NOW(),
                    updated_at TIMESTAMP DEFAULT NOW()
                )
                """);

        apply("CREATE UNIQUE INDEX IF NOT EXISTS idx_devices_status_cache_device ON devices_status_cache (device_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_devices_status_cache_status ON devices_status_cache (status, freshness_status, health_score)");
        apply("CREATE INDEX IF NOT EXISTS idx_devices_status_cache_location_role ON devices_status_cache (location, role_name)");
        apply("CREATE INDEX IF NOT EXISTS idx_device_health_snapshots_device_time ON device_health_snapshots (device_id, captured_at DESC)");
        apply("CREATE INDEX IF NOT EXISTS idx_device_health_snapshots_captured_time ON device_health_snapshots (captured_at DESC)");
        apply("CREATE UNIQUE INDEX IF NOT EXISTS idx_network_device_sync_status_device_module ON network_device_sync_status (device_id, module_name)");
        apply("CREATE INDEX IF NOT EXISTS idx_network_device_sync_status_module ON network_device_sync_status (module_name, status, last_attempt_at DESC)");
        apply("CREATE INDEX IF NOT EXISTS idx_device_maintenance_windows_device_time ON device_maintenance_windows (device_id, starts_at, ends_at)");
        apply("CREATE INDEX IF NOT EXISTS idx_device_maintenance_windows_active ON device_maintenance_windows (is_active, starts_at, ends_at)");
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("Skipping network schema sync for SQL [{}]: {}", sql, ex.getMessage());
        }
    }
}
