package com.netmaster.nmx.service;

import com.netmaster.nmx.config.TenantProvisioningProperties;
import com.netmaster.nmx.master.model.SubscriptionPlan;
import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.model.TenantDatabaseInfo;
import com.netmaster.nmx.master.model.TenantUserIndex;
import com.netmaster.nmx.master.repository.TenantDatabaseInfoRepository;
import com.netmaster.nmx.master.repository.TenantUserIndexRepository;
import com.netmaster.nmx.model.Role;
import com.netmaster.nmx.model.User;
import com.netmaster.nmx.repository.RoleRepository;
import com.netmaster.nmx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProvisioningService {

    @Qualifier("masterDataSource")
    private final DataSource masterDataSource;
    private final TenantProvisioningProperties provisioningProperties;
    private final TenantDatabaseInfoRepository tenantDatabaseInfoRepository;
    private final TenantConnectionManager tenantConnectionManager;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantUserIndexRepository tenantUserIndexRepository;
    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/postgres}")
    private String primaryJdbcUrl;
    @Value("${spring.datasource.username:postgres}")
    private String primaryDbUsername;
    @Value("${spring.datasource.password:}")
    private String primaryDbPassword;

    @Transactional("masterTransactionManager")
    public TenantDatabaseInfo provisionTenantDatabase(Tenant tenant,
                                                      SubscriptionPlan plan,
                                                      String adminName,
                                                      String adminEmail,
                                                      String adminUsername,
                                                      String passwordValue) {
        if (!provisioningProperties.isEnabled()) {
            log.info("Tenant provisioning skipped for tenant {} because tenant mode is disabled.", tenant.getSlug());
            return createPrimaryDatabasePlaceholder(tenant);
        }
        String dbName = ensureDatabaseName(tenant);
        createDatabaseIfMissing(dbName);

        TenantDatabaseInfo connection = tenantDatabaseInfoRepository.findByTenantId(tenant.getId())
                .orElseGet(TenantDatabaseInfo::new);
        connection.setTenantId(tenant.getId());
        connection.setDbName(dbName);
        connection.setDbHost(provisioningProperties.getHost());
        connection.setDbPort(provisioningProperties.getPort());
        connection.setDbUser(provisioningProperties.getAdminUsername());
        connection.setDbPasswordEncrypted(provisioningProperties.getAdminPassword());
        connection.setJdbcUrl(buildJdbcUrl(dbName));
        connection.setActive(true);
        TenantDatabaseInfo saved = tenantDatabaseInfoRepository.save(connection);
        syncTenantDatabaseMetadata(tenant, saved);

        tenantConnectionManager.activateTenant(tenant.getId());
        runTenantMigrations(saved);
        seedInitialTenantData(tenant, plan, adminName, adminEmail, adminUsername, passwordValue);
        return saved;
    }

    public void runTenantMigrations(TenantDatabaseInfo connection) {
        if (!provisioningProperties.isEnabled()) {
            return;
        }
        Flyway flyway = Flyway.configure()
                .dataSource(connection.getJdbcUrl(), connection.getDbUser(), connection.getDbPasswordEncrypted())
                .locations(provisioningProperties.getFlywayLocation())
                .baselineOnMigrate(true)
                .load();
        try {
            flyway.migrate();
        } catch (FlywayException ex) {
            if (!isChecksumValidationFailure(ex)) {
                throw ex;
            }

            log.warn("Tenant migration checksum mismatch detected for database {}. Running Flyway repair before retry.",
                    connection.getDbName());
            flyway.repair();
            flyway.migrate();
        }
        ensureSecuritySchema(connection);
    }

    public void seedInitialTenantData(Tenant tenant,
                                      SubscriptionPlan plan,
                                      String adminName,
                                      String adminEmail,
                                      String adminUsername,
                                      String passwordValue) {
        if (!provisioningProperties.isEnabled()) {
            return;
        }
        validateTenantAdminUsername(tenant, adminUsername);
        tenantConnectionManager.runInTenantContext(tenant, () -> {
            Role tenantAdminRole = ensureRole(provisioningProperties.getDefaultTenantAdminRole(), "Tenant administrator", "FULL");
            ensureRole("ROLE_TENANT_ADMIN", "Legacy tenant administrator", "FULL");
            ensureRole("ROLE_STAFF", "Tenant staff", "WRITE");
            ensureRole("ROLE_READONLY", "Read only tenant access", "READ");

            User admin = userRepository.findByUsername(adminUsername).orElseGet(User::new);
            admin.setFullName(adminName);
            admin.setUsername(adminUsername);
            admin.setEmail(adminEmail);
            admin.setPhone(tenant.getPhone());
            admin.setActive(true);
            admin.setPassword(resolvePasswordValue(passwordValue));
            Set<Role> roles = new HashSet<>();
            roles.add(tenantAdminRole);
            admin.setRoles(roles);
            User savedUser = userRepository.save(admin);

            TenantUserIndex index = tenantUserIndexRepository
                    .findByTenantIdAndUsernameIgnoreCase(tenant.getId(), savedUser.getUsername())
                    .orElseGet(TenantUserIndex::new);
            index.setTenantId(tenant.getId());
            index.setUserIdInTenantDb(savedUser.getId());
            index.setEmail(adminEmail);
            index.setUsername(savedUser.getUsername());
            index.setRole(tenantAdminRole.getName());
            index.setStatus("ACTIVE");
            tenantUserIndexRepository.save(index);
        });
    }

    private void validateTenantAdminUsername(Tenant tenant, String adminUsername) {
        tenantUserIndexRepository.findByUsernameIgnoreCase(adminUsername).stream()
                .filter(existing -> existing.getTenantId() != null)
                .filter(existing -> tenant.getId() == null || !existing.getTenantId().equals(tenant.getId()))
                .findFirst()
                .ifPresent(existing -> {
                    throw new IllegalStateException("Username admin tenant '" + adminUsername + "' sudah digunakan tenant lain.");
                });
    }

    private Role ensureRole(String name, String description, String level) {
        return roleRepository.findByName(name)
                .orElseGet(() -> {
                    Role created = new Role();
                    created.setName(name);
                    created.setDescription(description);
                    created.setPermissionsLevel(level);
                    return roleRepository.save(created);
                });
    }

    private String resolvePasswordValue(String passwordValue) {
        if (passwordValue != null && passwordValue.startsWith("$2")) {
            return passwordValue;
        }
        return passwordEncoder.encode(passwordValue);
    }

    private void createDatabaseIfMissing(String dbName) {
        String adminUrl = buildAdminJdbcUrl();
        try (Connection connection = DriverManager.getConnection(
                adminUrl,
                provisioningProperties.getAdminUsername(),
                provisioningProperties.getAdminPassword())) {
            connection.setAutoCommit(true);
            if (databaseExists(connection, dbName)) {
                log.info("Tenant database {} already exists on PostgreSQL {}:{}", dbName,
                        provisioningProperties.getHost(), provisioningProperties.getPort());
                return;
            }
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("CREATE DATABASE " + quoteIdentifier(dbName));
            }
        } catch (SQLException ex) {
            String detail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            throw new IllegalStateException(
                    "Gagal create database " + dbName
                            + " di PostgreSQL "
                            + provisioningProperties.getHost()
                            + ":"
                            + provisioningProperties.getPort()
                            + " dengan user "
                            + provisioningProperties.getAdminUsername()
                            + " - "
                            + detail,
                    ex
            );
        }
    }

    private String ensureDatabaseName(Tenant tenant) {
        if (tenant.getDbName() != null && !tenant.getDbName().isBlank()) {
            return tenant.getDbName();
        }
        String source = tenant.getCompanyName() == null || tenant.getCompanyName().isBlank()
                ? tenant.getSlug()
                : tenant.getCompanyName();
        String normalized = source.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (normalized.isBlank()) {
            normalized = "tenant_" + tenant.getId();
        }
        if (!Character.isLetter(normalized.charAt(0)) && normalized.charAt(0) != '_') {
            normalized = "tenant_" + normalized;
        }
        if (normalized.length() > 63) {
            normalized = normalized.substring(0, 63).replaceAll("_+$", "");
        }
        return normalized;
    }

    private String buildJdbcUrl(String dbName) {
        StringBuilder builder = new StringBuilder("jdbc:postgresql://")
                .append(provisioningProperties.getHost())
                .append(":")
                .append(provisioningProperties.getPort())
                .append("/")
                .append(dbName);
        if (provisioningProperties.getTenantJdbcParameters() != null
                && !provisioningProperties.getTenantJdbcParameters().isBlank()) {
            builder.append("?").append(provisioningProperties.getTenantJdbcParameters());
        }
        return builder.toString();
    }

    private String buildAdminJdbcUrl() {
        StringBuilder builder = new StringBuilder("jdbc:postgresql://")
                .append(provisioningProperties.getHost())
                .append(":")
                .append(provisioningProperties.getPort())
                .append("/postgres");
        if (provisioningProperties.getTenantJdbcParameters() != null
                && !provisioningProperties.getTenantJdbcParameters().isBlank()) {
            builder.append("?").append(provisioningProperties.getTenantJdbcParameters());
        }
        return builder.toString();
    }

    private void ensureSecuritySchema(TenantDatabaseInfo connection) {
        try (Connection jdbc = DriverManager.getConnection(
                connection.getJdbcUrl(),
                connection.getDbUser(),
                connection.getDbPasswordEncrypted()
        ); Statement statement = jdbc.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS roles (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        name VARCHAR(50) NOT NULL UNIQUE,
                        description TEXT,
                        permissions_level VARCHAR(20) NOT NULL DEFAULT 'READ'
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                        employee_id VARCHAR(50),
                        full_name VARCHAR(150),
                        username VARCHAR(100) NOT NULL UNIQUE,
                        password VARCHAR(255) NOT NULL,
                        email VARCHAR(150),
                        phone VARCHAR(30),
                        is_active BOOLEAN NOT NULL DEFAULT TRUE
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS user_roles (
                        user_id BIGINT NOT NULL,
                        role_id BIGINT NOT NULL,
                        PRIMARY KEY (user_id, role_id)
                    )
                    """);
        } catch (SQLException ex) {
            throw new IllegalStateException("Gagal memastikan schema auth tenant tersedia: " + ex.getMessage(), ex);
        }
    }

    private boolean databaseExists(Connection connection, String dbName) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("select 1 from pg_database where datname = ?")) {
            statement.setString(1, dbName);
            return statement.executeQuery().next();
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private boolean isChecksumValidationFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("checksum mismatch")
                        || normalized.contains("migrations have failed validation")
                        || normalized.contains("validate failed")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private TenantDatabaseInfo createPrimaryDatabasePlaceholder(Tenant tenant) {
        TenantDatabaseInfo connection = tenantDatabaseInfoRepository.findByTenantId(tenant.getId())
                .orElseGet(TenantDatabaseInfo::new);
        connection.setTenantId(tenant.getId());
        connection.setDbName("nmx_db_primary_" + tenant.getId());
        connection.setDbHost(provisioningProperties.getHost());
        connection.setDbPort(provisioningProperties.getPort());
        connection.setDbUser(primaryDbUsername);
        connection.setDbPasswordEncrypted(primaryDbPassword);
        connection.setJdbcUrl(primaryJdbcUrl);
        connection.setActive(false);
        TenantDatabaseInfo saved = tenantDatabaseInfoRepository.save(connection);
        syncTenantDatabaseMetadata(tenant, saved);
        return saved;
    }

    private void syncTenantDatabaseMetadata(Tenant tenant, TenantDatabaseInfo connection) {
        tenant.setDbName(connection.getDbName());
        tenant.setDbHost(connection.getDbHost());
        tenant.setDbUser(connection.getDbUser());
        tenant.setDbPassword(connection.getDbPasswordEncrypted());
    }
}
