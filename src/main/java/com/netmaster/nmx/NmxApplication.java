package com.netmaster.nmx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.netmaster.nmx.config.WhatsappGatewayBootstrapProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(WhatsappGatewayBootstrapProperties.class)
public class NmxApplication {

	public static void main(String[] args) {
		SpringApplication.run(NmxApplication.class, args);
	}

}
