package com.netmaster.nmx.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "nmx.master.datasource")
public class MasterDatabaseProperties {

    private String url;
    private String username;
    private String password;
    private String driverClassName = "org.postgresql.Driver";
}
