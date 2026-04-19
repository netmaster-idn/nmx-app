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
public class AppLogSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        apply("""
                CREATE TABLE IF NOT EXISTS app_activity_logs (
                    id BIGSERIAL PRIMARY KEY,
                    username VARCHAR(100),
                    full_name VARCHAR(150),
                    role_names VARCHAR(250),
                    module_name VARCHAR(80),
                    action_type VARCHAR(40),
                    action_label VARCHAR(180),
                    http_method VARCHAR(12),
                    request_path VARCHAR(255),
                    status_code INTEGER,
                    status VARCHAR(20),
                    ip_address VARCHAR(80),
                    duration_ms BIGINT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                )
                """);
        apply("""
                CREATE TABLE IF NOT EXISTS app_error_logs (
                    id BIGSERIAL PRIMARY KEY,
                    username VARCHAR(100),
                    full_name VARCHAR(150),
                    role_names VARCHAR(250),
                    module_name VARCHAR(80),
                    action_label VARCHAR(180),
                    http_method VARCHAR(12),
                    request_path VARCHAR(255),
                    status_code INTEGER,
                    error_type VARCHAR(160),
                    error_message VARCHAR(1000),
                    root_cause_message VARCHAR(1000),
                    stack_trace TEXT,
                    ip_address VARCHAR(80),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                )
                """);
        apply("CREATE INDEX IF NOT EXISTS idx_app_activity_logs_created_at ON app_activity_logs (created_at DESC)");
        apply("CREATE INDEX IF NOT EXISTS idx_app_activity_logs_username ON app_activity_logs (username)");
        apply("CREATE INDEX IF NOT EXISTS idx_app_error_logs_created_at ON app_error_logs (created_at DESC)");
        apply("CREATE INDEX IF NOT EXISTS idx_app_error_logs_username ON app_error_logs (username)");
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("Skipping app log schema sync for SQL [{}]: {}", sql, ex.getMessage());
        }
    }
}
