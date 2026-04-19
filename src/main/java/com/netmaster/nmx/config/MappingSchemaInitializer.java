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
public class MappingSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        apply("ALTER TABLE IF EXISTS odps ADD COLUMN IF NOT EXISTS splitter VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS odps ADD COLUMN IF NOT EXISTS company_profile_id BIGINT");
        apply("ALTER TABLE IF EXISTS odps ADD COLUMN IF NOT EXISTS node_type VARCHAR(10)");
        apply("ALTER TABLE IF EXISTS customer_services ADD COLUMN IF NOT EXISTS ont_redaman DECIMAL(6,2)");
        apply("ALTER TABLE IF EXISTS customer_services ADD COLUMN IF NOT EXISTS wifi_name VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS customer_services ADD COLUMN IF NOT EXISTS wifi_password VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS customer_services ADD COLUMN IF NOT EXISTS ont_standby_since TIMESTAMP");
        apply("ALTER TABLE IF EXISTS customer_services ADD COLUMN IF NOT EXISTS last_restart_requested_at TIMESTAMP");
        apply("ALTER TABLE IF EXISTS customer_services ADD COLUMN IF NOT EXISTS technician_id BIGINT");
        apply("ALTER TABLE IF EXISTS tickets ADD COLUMN IF NOT EXISTS technician_id BIGINT");
        apply("ALTER TABLE IF EXISTS tickets ADD COLUMN IF NOT EXISTS sla_deadline TIMESTAMP");
        apply("ALTER TABLE IF EXISTS tickets ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMP");
        apply("ALTER TABLE IF EXISTS tickets ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP");
        apply("ALTER TABLE IF EXISTS tickets ADD COLUMN IF NOT EXISTS resolution_notes TEXT");
        apply("""
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
                """);

        apply("""
                UPDATE odcs
                SET code = 'ODC-' || TO_CHAR(COALESCE(created_at, CURRENT_TIMESTAMP), 'YYYYMM') || '-' || LPAD(id::TEXT, 4, '0')
                WHERE code IS NULL OR BTRIM(code) = ''
                """);
        apply("""
                UPDATE odps
                SET code = 'ODP-' || TO_CHAR(COALESCE(created_at, CURRENT_TIMESTAMP), 'YYYYMM') || '-' || LPAD(id::TEXT, 4, '0')
                WHERE code IS NULL OR BTRIM(code) = ''
                """);
        apply("""
                UPDATE odps
                SET node_type = 'ODP'
                WHERE node_type IS NULL OR BTRIM(node_type) = ''
                """);
        apply("""
                UPDATE customer_services
                SET wifi_name = COALESCE(NULLIF(BTRIM(wifi_name), ''), pppoe_username),
                    wifi_password = COALESCE(NULLIF(BTRIM(wifi_password), ''), pppoe_password)
                WHERE (wifi_name IS NULL OR BTRIM(wifi_name) = '' OR wifi_password IS NULL OR BTRIM(wifi_password) = '')
                """);

        apply("""
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
                )
                """);
        apply("""
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_network_topology_links_pair') THEN
                        CREATE UNIQUE INDEX idx_network_topology_links_pair
                            ON network_topology_links (from_node_type, from_node_id, to_node_type, to_node_id);
                    END IF;
                END $$;
                """);

        apply("""
                CREATE TABLE IF NOT EXISTS ont_action_logs (
                    id BIGSERIAL PRIMARY KEY,
                    customer_service_id BIGINT NOT NULL REFERENCES customer_services(id) ON DELETE CASCADE,
                    action_type VARCHAR(40) NOT NULL,
                    payload TEXT,
                    requested_by VARCHAR(100),
                    status VARCHAR(30) DEFAULT 'queued',
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        apply("""
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_indexes WHERE indexname = 'idx_ont_action_logs_service') THEN
                        CREATE INDEX idx_ont_action_logs_service ON ont_action_logs (customer_service_id);
                    END IF;
                END $$;
                """);

        apply("""
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
                """);

        apply("""
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
                """);

        apply("""
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trigger_generate_odc_code') THEN
                        CREATE TRIGGER trigger_generate_odc_code
                            BEFORE INSERT ON odcs
                            FOR EACH ROW
                            EXECUTE FUNCTION generate_odc_code();
                    END IF;
                END $$;
                """);

        apply("""
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trigger_generate_odp_code') THEN
                        CREATE TRIGGER trigger_generate_odp_code
                            BEFORE INSERT ON odps
                            FOR EACH ROW
                            EXECUTE FUNCTION generate_odp_code();
                    END IF;
                END $$;
                """);
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("Skipping mapping schema sync for SQL [{}]: {}", sql, ex.getMessage());
        }
    }
}
