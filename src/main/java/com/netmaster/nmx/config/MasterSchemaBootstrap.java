package com.netmaster.nmx.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component("masterSchemaBootstrap")
@RequiredArgsConstructor
@Slf4j
public class MasterSchemaBootstrap {

    @Qualifier("masterDataSource")
    private final DataSource masterDataSource;
    private final ResourceLoader resourceLoader;

    @Value("${nmx.master.schema.auto-init:true}")
    private boolean autoInit;

    @Value("${nmx.master.schema.script:classpath:sql/saas/master-schema.sql}")
    private String schemaScript;

    @PostConstruct
    public void initializeSchema() {
        if (!autoInit) {
            log.info("Master schema auto init is disabled.");
            return;
        }

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setContinueOnError(false);
        populator.addScript(resourceLoader.getResource(schemaScript));
        populator.execute(masterDataSource);
        log.info("Master SaaS schema initialized from {}", schemaScript);
    }
}
