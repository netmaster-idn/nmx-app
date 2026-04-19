package com.netmaster.nmx.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "nmx.crypto")
public class TextEncryptionProperties {

    private String secret = "nmx-default-text-encryption-key-32";
}
