package com.netmaster.nmx.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "nmx.whatsapp.gateway")
@Getter
@Setter
public class WhatsappGatewayProperties {

    private String baseUrl = "http://127.0.0.1:3001";
    private int timeoutMs = 15000;
}
