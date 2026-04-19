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
public class TechnicianSchemaInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        apply("ALTER TABLE IF EXISTS technicians ADD COLUMN IF NOT EXISTS area VARCHAR(100)");
        apply("ALTER TABLE IF EXISTS technicians ADD COLUMN IF NOT EXISTS status VARCHAR(20)");
        apply("""
                UPDATE technicians
                SET status = CASE
                    WHEN COALESCE(is_active, true) = false THEN 'inactive'
                    ELSE COALESCE(NULLIF(BTRIM(status), ''), 'active')
                END
                WHERE status IS NULL OR BTRIM(status) = ''
                """);
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception ex) {
            log.warn("Skipping technician schema sync for SQL [{}]: {}", sql, ex.getMessage());
        }
    }
}
