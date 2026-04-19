package com.netmaster.nmx.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "nmx.whatsapp.gateway.bootstrap")
public class WhatsappGatewayBootstrapProperties {

    private boolean enabled = true;
    private boolean autoInstall = true;
    private boolean autoInitClient = true;
    private String directory = "whatsapp-gateway";
    private String host = "127.0.0.1";
    private int port = 3001;
    private int startupWaitSeconds = 15;
    private int installTimeoutMinutes = 10;
}
