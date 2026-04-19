package com.netmaster.nmx.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;

@Component
@ConditionalOnProperty(name = "nmx.runtime.schema-sync.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class MikrotikHybridSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(String... args) {
        apply("""
                CREATE TABLE IF NOT EXISTS mikrotik_devices (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    device_name VARCHAR(100),
                    ip_address VARCHAR(45),
                    vpn_ip_address VARCHAR(100),
                    winbox_ip_address VARCHAR(45),
                    api_ip_address VARCHAR(45),
                    api_port INTEGER DEFAULT 8728,
                    api_username VARCHAR(50),
                    api_password VARCHAR(100),
                    monitoring_target VARCHAR(20) DEFAULT 'vpn',
                    username VARCHAR(50),
                    password VARCHAR(100),
                    location VARCHAR(200),
                    site_name VARCHAR(200),
                    description VARCHAR(500),
                    notes VARCHAR(500),
                    status VARCHAR(20) DEFAULT 'offline',
                    current_status VARCHAR(20) DEFAULT 'offline',
                    routerboard_version VARCHAR(255),
                    ros_version VARCHAR(255),
                    cpu_load INTEGER,
                    memory_used BIGINT,
                    memory_total BIGINT,
                    uptime_seconds BIGINT,
                    last_monitored TIMESTAMP,
                    last_seen_at TIMESTAMP,
                    last_snmp_sync_at TIMESTAMP,
                    last_api_sync_at TIMESTAMP,
                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                    snmp_version VARCHAR(20) DEFAULT '2c',
                    snmp_community VARCHAR(100),
                    snmp_port INTEGER DEFAULT 161,
                    polling_interval_snmp INTEGER DEFAULT 60,
                    sync_interval_api INTEGER DEFAULT 120,
                    snmp_enabled BOOLEAN DEFAULT TRUE,
                    api_enabled BOOLEAN DEFAULT TRUE,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """);
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS device_name VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS site_name VARCHAR(200)");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS api_port INTEGER DEFAULT 8728");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS api_username VARCHAR(50)");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS api_password VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS snmp_version VARCHAR(20) DEFAULT '2c'");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS snmp_community VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS snmp_port INTEGER DEFAULT 161");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS notes VARCHAR(500)");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS polling_interval_snmp INTEGER DEFAULT 60");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS sync_interval_api INTEGER DEFAULT 120");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMP");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS last_snmp_sync_at TIMESTAMP");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS last_api_sync_at TIMESTAMP");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS current_status VARCHAR(20)");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS snmp_enabled BOOLEAN DEFAULT TRUE");
        apply("ALTER TABLE IF EXISTS mikrotik_devices ADD COLUMN IF NOT EXISTS api_enabled BOOLEAN DEFAULT TRUE");
        apply("UPDATE mikrotik_devices SET device_name = COALESCE(device_name, name)");
        apply("UPDATE mikrotik_devices SET site_name = COALESCE(site_name, location)");
        apply("UPDATE mikrotik_devices SET api_username = COALESCE(api_username, username)");
        apply("UPDATE mikrotik_devices SET api_password = COALESCE(api_password, password)");
        apply("UPDATE mikrotik_devices SET current_status = COALESCE(current_status, status, 'offline')");
        migrateLegacyVpnEndpoint();

        apply("""
                CREATE TABLE IF NOT EXISTS mikrotik_device_metrics (
                    id BIGSERIAL PRIMARY KEY,
                    device_id BIGINT NOT NULL,
                    cpu_load INTEGER,
                    memory_total BIGINT,
                    memory_used BIGINT,
                    memory_free BIGINT,
                    uptime BIGINT,
                    temperature NUMERIC(10,2),
                    voltage NUMERIC(10,2),
                    board_health VARCHAR(100),
                    collected_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    source VARCHAR(20) NOT NULL DEFAULT 'snmp'
                )
                """);
        apply("""
                CREATE TABLE IF NOT EXISTS mikrotik_interfaces (
                    id BIGSERIAL PRIMARY KEY,
                    device_id BIGINT NOT NULL,
                    interface_name VARCHAR(100) NOT NULL,
                    interface_index INTEGER NOT NULL,
                    interface_type VARCHAR(50),
                    is_monitored BOOLEAN NOT NULL DEFAULT TRUE,
                    priority INTEGER NOT NULL DEFAULT 100,
                    comment VARCHAR(255),
                    admin_status VARCHAR(20),
                    oper_status VARCHAR(20),
                    last_seen_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """);
        apply("""
                CREATE TABLE IF NOT EXISTS mikrotik_interface_traffic (
                    id BIGSERIAL PRIMARY KEY,
                    device_id BIGINT NOT NULL,
                    interface_id BIGINT NOT NULL,
                    in_octets BIGINT,
                    out_octets BIGINT,
                    in_bps BIGINT,
                    out_bps BIGINT,
                    collected_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    source VARCHAR(20) NOT NULL DEFAULT 'snmp'
                )
                """);
        apply("""
                CREATE TABLE IF NOT EXISTS mikrotik_pppoe_sessions (
                    id BIGSERIAL PRIMARY KEY,
                    device_id BIGINT NOT NULL,
                    username VARCHAR(100) NOT NULL,
                    ip_address VARCHAR(45),
                    caller_id VARCHAR(100),
                    session_id VARCHAR(100),
                    profile_name VARCHAR(100),
                    service VARCHAR(50),
                    status VARCHAR(20),
                    login_at TIMESTAMP,
                    logout_at TIMESTAMP,
                    last_seen TIMESTAMP,
                    last_sync_at TIMESTAMP,
                    synced_at TIMESTAMP,
                    source VARCHAR(20) NOT NULL DEFAULT 'api'
                )
                """);
        apply("""
                CREATE TABLE IF NOT EXISTS mikrotik_pppoe_events (
                    id BIGSERIAL PRIMARY KEY,
                    device_id BIGINT NOT NULL,
                    username VARCHAR(100),
                    event_type VARCHAR(30),
                    ip_address VARCHAR(45),
                    caller_id VARCHAR(100),
                    profile VARCHAR(100),
                    severity VARCHAR(30),
                    raw_payload TEXT,
                    raw_message TEXT,
                    event_time TIMESTAMP,
                    fingerprint_hash VARCHAR(128),
                    synced_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    source VARCHAR(20) NOT NULL DEFAULT 'api'
                )
                """);
        apply("""
                CREATE TABLE IF NOT EXISTS device_sync_status (
                    id BIGSERIAL PRIMARY KEY,
                    device_id BIGINT NOT NULL,
                    module_name VARCHAR(50) NOT NULL,
                    last_success_at TIMESTAMP,
                    last_attempt_at TIMESTAMP,
                    last_error VARCHAR(500),
                    stale_after_seconds INTEGER,
                    status VARCHAR(20) NOT NULL DEFAULT 'idle',
                    consecutive_failures INTEGER NOT NULL DEFAULT 0,
                    next_retry_at TIMESTAMP,
                    last_duration_ms BIGINT,
                    last_item_count INTEGER
                )
                """);
        apply("ALTER TABLE IF EXISTS mikrotik_interfaces ADD COLUMN IF NOT EXISTS interface_type VARCHAR(50)");
        apply("ALTER TABLE IF EXISTS mikrotik_interfaces ADD COLUMN IF NOT EXISTS is_monitored BOOLEAN DEFAULT TRUE");
        apply("ALTER TABLE IF EXISTS mikrotik_interfaces ADD COLUMN IF NOT EXISTS priority INTEGER DEFAULT 100");
        apply("ALTER TABLE IF EXISTS mikrotik_interfaces ADD COLUMN IF NOT EXISTS comment VARCHAR(255)");
        apply("ALTER TABLE IF EXISTS mikrotik_pppoe_sessions ADD COLUMN IF NOT EXISTS service VARCHAR(50)");
        apply("ALTER TABLE IF EXISTS mikrotik_pppoe_sessions ADD COLUMN IF NOT EXISTS last_seen TIMESTAMP");
        apply("ALTER TABLE IF EXISTS mikrotik_pppoe_sessions ADD COLUMN IF NOT EXISTS synced_at TIMESTAMP");
        apply("ALTER TABLE IF EXISTS mikrotik_pppoe_events ADD COLUMN IF NOT EXISTS profile VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS mikrotik_pppoe_events ADD COLUMN IF NOT EXISTS severity VARCHAR(30)");
        apply("ALTER TABLE IF EXISTS mikrotik_pppoe_events ADD COLUMN IF NOT EXISTS raw_message TEXT");
        apply("ALTER TABLE IF EXISTS mikrotik_pppoe_events ADD COLUMN IF NOT EXISTS fingerprint_hash VARCHAR(128)");
        apply("ALTER TABLE IF EXISTS mikrotik_pppoe_events ADD COLUMN IF NOT EXISTS synced_at TIMESTAMP");
        apply("ALTER TABLE IF EXISTS mikrotik_pppoe_events ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT NOW()");

        apply("CREATE INDEX IF NOT EXISTS idx_mikrotik_device_metrics_device_time ON mikrotik_device_metrics (device_id, collected_at DESC)");
        apply("CREATE INDEX IF NOT EXISTS idx_mikrotik_devices_status ON mikrotik_devices (status)");
        apply("CREATE INDEX IF NOT EXISTS idx_mikrotik_devices_current_status ON mikrotik_devices (current_status)");
        apply("CREATE INDEX IF NOT EXISTS idx_mikrotik_devices_active ON mikrotik_devices (is_active)");
        apply("CREATE UNIQUE INDEX IF NOT EXISTS idx_mikrotik_interfaces_device_index ON mikrotik_interfaces (device_id, interface_index)");
        apply("CREATE INDEX IF NOT EXISTS idx_mikrotik_interfaces_monitored_priority ON mikrotik_interfaces (is_monitored, priority, interface_name)");
        apply("CREATE INDEX IF NOT EXISTS idx_mikrotik_interface_traffic_iface_time ON mikrotik_interface_traffic (interface_id, collected_at DESC)");
        apply("CREATE INDEX IF NOT EXISTS idx_mikrotik_interface_traffic_device_time ON mikrotik_interface_traffic (device_id, collected_at DESC)");
        apply("CREATE UNIQUE INDEX IF NOT EXISTS idx_mikrotik_pppoe_sessions_device_username ON mikrotik_pppoe_sessions (device_id, username)");
        apply("CREATE INDEX IF NOT EXISTS idx_mikrotik_pppoe_sessions_status_sync ON mikrotik_pppoe_sessions (status, last_sync_at DESC)");
        apply("CREATE INDEX IF NOT EXISTS idx_mikrotik_pppoe_events_device_time ON mikrotik_pppoe_events (device_id, event_time DESC)");
        apply("CREATE UNIQUE INDEX IF NOT EXISTS idx_mikrotik_pppoe_events_fingerprint ON mikrotik_pppoe_events (fingerprint_hash)");
        apply("CREATE UNIQUE INDEX IF NOT EXISTS idx_device_sync_status_device_module ON device_sync_status (device_id, module_name)");

        normalizePppoeTextColumns();
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("Skipping MikroTik hybrid schema sync for SQL [{}]: {}", sql, ex.getMessage());
        }
    }

    private void migrateLegacyVpnEndpoint() {
        if (!columnExists("mikrotik_devices", "vpn_port")) {
            return;
        }
        apply("""
                UPDATE mikrotik_devices
                SET vpn_ip_address = CASE
                    WHEN vpn_ip_address IS NULL OR TRIM(vpn_ip_address) = '' THEN
                        CASE
                            WHEN api_ip_address IS NULL OR TRIM(api_ip_address) = '' THEN
                                CASE
                                    WHEN ip_address IS NULL OR TRIM(ip_address) = '' THEN vpn_ip_address
                                    ELSE CONCAT(TRIM(ip_address), ':', COALESCE(NULLIF(vpn_port, 0), NULLIF(api_port, 0), 8728))
                                END
                            ELSE CONCAT(TRIM(api_ip_address), ':', COALESCE(NULLIF(vpn_port, 0), NULLIF(api_port, 0), 8728))
                        END
                    WHEN POSITION(':' IN vpn_ip_address) > 0 THEN TRIM(vpn_ip_address)
                    ELSE CONCAT(TRIM(vpn_ip_address), ':', COALESCE(NULLIF(vpn_port, 0), NULLIF(api_port, 0), 8728))
                END
                """);
    }

    private void normalizePppoeTextColumns() {
        List<ColumnSpec> varcharColumns = List.of(
                new ColumnSpec("mikrotik_pppoe_sessions", "username", "VARCHAR(100)"),
                new ColumnSpec("mikrotik_pppoe_sessions", "ip_address", "VARCHAR(45)"),
                new ColumnSpec("mikrotik_pppoe_sessions", "caller_id", "VARCHAR(100)"),
                new ColumnSpec("mikrotik_pppoe_sessions", "session_id", "VARCHAR(100)"),
                new ColumnSpec("mikrotik_pppoe_sessions", "profile_name", "VARCHAR(100)"),
                new ColumnSpec("mikrotik_pppoe_sessions", "service", "VARCHAR(50)"),
                new ColumnSpec("mikrotik_pppoe_sessions", "status", "VARCHAR(20)"),
                new ColumnSpec("mikrotik_pppoe_sessions", "source", "VARCHAR(20)"),
                new ColumnSpec("mikrotik_pppoe_events", "username", "VARCHAR(100)"),
                new ColumnSpec("mikrotik_pppoe_events", "event_type", "VARCHAR(30)"),
                new ColumnSpec("mikrotik_pppoe_events", "ip_address", "VARCHAR(45)"),
                new ColumnSpec("mikrotik_pppoe_events", "caller_id", "VARCHAR(100)"),
                new ColumnSpec("mikrotik_pppoe_events", "profile", "VARCHAR(100)"),
                new ColumnSpec("mikrotik_pppoe_events", "severity", "VARCHAR(30)"),
                new ColumnSpec("mikrotik_pppoe_events", "fingerprint_hash", "VARCHAR(128)"),
                new ColumnSpec("mikrotik_pppoe_events", "source", "VARCHAR(20)")
        );
        List<ColumnSpec> textColumns = List.of(
                new ColumnSpec("mikrotik_pppoe_events", "raw_payload", "TEXT"),
                new ColumnSpec("mikrotik_pppoe_events", "raw_message", "TEXT")
        );

        varcharColumns.forEach(this::ensureTextualColumn);
        textColumns.forEach(this::ensureTextualColumn);
    }

    private void ensureTextualColumn(ColumnSpec spec) {
        String udtName = getColumnUdtName(spec.tableName(), spec.columnName());
        if (udtName == null) {
            return;
        }

        if ("bytea".equalsIgnoreCase(udtName)) {
            apply("""
                    ALTER TABLE %s
                    ALTER COLUMN %s TYPE %s
                    USING CASE
                        WHEN %s IS NULL THEN NULL
                        ELSE convert_from(%s, 'UTF8')
                    END
                    """.formatted(spec.tableName(), spec.columnName(), spec.targetType(), spec.columnName(), spec.columnName()));
            return;
        }

        if (!isTextualType(udtName, spec.targetType())) {
            apply("""
                    ALTER TABLE %s
                    ALTER COLUMN %s TYPE %s
                    USING CASE
                        WHEN %s IS NULL THEN NULL
                        ELSE %s::text
                    END
                    """.formatted(spec.tableName(), spec.columnName(), spec.targetType(), spec.columnName(), spec.columnName()));
            return;
        }

        apply("ALTER TABLE %s ALTER COLUMN %s TYPE %s".formatted(spec.tableName(), spec.columnName(), spec.targetType()));
    }

    private boolean isTextualType(String udtName, String targetType) {
        String normalized = udtName.toLowerCase();
        return normalized.equals("varchar") || normalized.equals("text") || normalized.equals("bpchar")
                || normalized.equals(targetType.toLowerCase());
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE lower(table_name) = lower(?)
                  AND lower(column_name) = lower(?)
                """, Integer.class, tableName, columnName);
        return count != null && count > 0;
    }

    private String getColumnUdtName(String tableName, String columnName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String schema = connection.getSchema();
            try (ResultSet columns = metaData.getColumns(connection.getCatalog(), schema, tableName, columnName)) {
                if (columns.next()) {
                    String typeName = columns.getString("TYPE_NAME");
                    if (typeName != null && !typeName.isBlank()) {
                        return typeName;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Unable to inspect column type for {}.{}: {}", tableName, columnName, ex.getMessage());
        }
        List<String> result = jdbcTemplate.query("""
                SELECT data_type
                FROM information_schema.columns
                WHERE lower(table_name) = lower(?)
                  AND lower(column_name) = lower(?)
                """, (rs, rowNum) -> rs.getString("data_type"), tableName, columnName);
        return result.isEmpty() ? null : result.getFirst();
    }

    private record ColumnSpec(String tableName, String columnName, String targetType) {
    }
}
