package com.netmaster.nmx.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableTransactionManagement
@EnableConfigurationProperties(MasterDatabaseProperties.class)
@EnableJpaRepositories(
        basePackages = "com.netmaster.nmx.master.repository",
        entityManagerFactoryRef = "masterEntityManagerFactory",
        transactionManagerRef = "masterTransactionManager"
)
public class MasterPersistenceConfig {

    @Value("${spring.jpa.properties.hibernate.dialect:org.hibernate.dialect.H2Dialect}")
    private String hibernateDialect;

    @Bean
    @ConfigurationProperties("nmx.master.datasource")
    public DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "masterDataSource")
    @ConfigurationProperties("nmx.master.datasource.hikari")
    public DataSource masterDataSource(@Qualifier("masterDataSourceProperties") DataSourceProperties properties) {
        ensureMasterDatabaseExists(properties);
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "masterEntityManagerFactory")
    @DependsOn("masterSchemaBootstrap")
    public LocalContainerEntityManagerFactoryBean masterEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("masterDataSource") DataSource dataSource) {

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.dialect", hibernateDialect);

        return builder
                .dataSource(dataSource)
                .packages("com.netmaster.nmx.master.model")
                .persistenceUnit("master")
                .properties(properties)
                .build();
    }

    @Bean(name = "masterTransactionManager")
    @DependsOn("masterSchemaBootstrap")
    public PlatformTransactionManager masterTransactionManager(
            @Qualifier("masterEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    @DependsOn("masterSchemaBootstrap")
    public JdbcTemplate masterJdbcTemplate(@Qualifier("masterDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private void ensureMasterDatabaseExists(DataSourceProperties properties) {
        String url = properties.getUrl();
        if (url == null || !url.startsWith("jdbc:postgresql://")) {
            return;
        }

        String databaseName = extractDatabaseName(url);
        String serverUrl = extractPostgresAdminUrl(url);
        if (databaseName == null || databaseName.isBlank() || serverUrl == null || serverUrl.isBlank()) {
            return;
        }
        if ("postgres".equalsIgnoreCase(databaseName)) {
            return;
        }

        try (Connection connection = DriverManager.getConnection(
                serverUrl,
                properties.getUsername(),
                properties.getPassword())) {
            connection.setAutoCommit(true);
            if (databaseExists(connection, databaseName)) {
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE " + quoteIdentifier(databaseName));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to ensure master database exists: " + databaseName, ex);
        }
    }

    private String extractDatabaseName(String url) {
        int prefixIndex = url.indexOf("://");
        if (prefixIndex < 0) {
            return null;
        }
        int pathStart = url.indexOf('/', prefixIndex + 3);
        if (pathStart < 0 || pathStart == url.length() - 1) {
            return null;
        }
        int queryStart = url.indexOf('?', pathStart);
        String database = queryStart >= 0 ? url.substring(pathStart + 1, queryStart) : url.substring(pathStart + 1);
        return database.isBlank() ? null : database;
    }

    private String extractPostgresAdminUrl(String url) {
        int prefixIndex = url.indexOf("://");
        if (prefixIndex < 0) {
            return null;
        }
        int pathStart = url.indexOf('/', prefixIndex + 3);
        if (pathStart < 0) {
            return null;
        }
        int queryStart = url.indexOf('?', pathStart);
        String query = queryStart >= 0 ? url.substring(queryStart + 1) : "";
        StringBuilder builder = new StringBuilder(url.substring(0, pathStart + 1)).append("postgres");
        if (!query.isBlank()) {
            builder.append('?').append(query);
        }
        return builder.toString();
    }

    private boolean databaseExists(Connection connection, String databaseName) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("select 1 from pg_database where datname = ?")) {
            statement.setString(1, databaseName);
            return statement.executeQuery().next();
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
