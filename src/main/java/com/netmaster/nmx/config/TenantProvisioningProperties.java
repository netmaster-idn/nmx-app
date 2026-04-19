package com.netmaster.nmx.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "nmx.tenant")
public class TenantProvisioningProperties {

    private boolean enabled = true;
    private String host = "localhost";
    private Integer port = 5432;
    private String adminUsername = "postgres";
    private String adminPassword = "Domboot007@";
    private String driverClassName = "org.postgresql.Driver";
    private String tenantJdbcParameters = "";
    private String flywayLocation = "classpath:db/migration";
    private String defaultPlanCode = "TRIAL";
    private String defaultTenantAdminRole = "ROLE_TENANT_SUPER_ADMIN";
    private String supportImpersonationRole = "ROLE_SUPPORT_READONLY";
    private Integer supportSessionMinutes = 30;
    private boolean preloadActiveDataSources = false;
    private Integer maxPoolSize = 4;
    private Integer minIdle = 0;
    private Long connectionTimeoutMs = 15000L;
    private Long idleTimeoutMs = 60000L;
    private Long maxLifetimeMs = 300000L;
}
