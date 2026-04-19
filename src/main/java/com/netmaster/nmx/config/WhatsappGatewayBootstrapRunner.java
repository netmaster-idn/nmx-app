package com.netmaster.nmx.config;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.WhatsappStatusData;
import com.netmaster.nmx.service.WhatsappGatewayBootstrapService;
import com.netmaster.nmx.service.WhatsappGatewayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nmx.whatsapp.gateway.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class WhatsappGatewayBootstrapRunner implements ApplicationRunner {

    private final WhatsappGatewayBootstrapService bootstrapService;
    private final WhatsappGatewayBootstrapProperties bootstrapProperties;
    private final WhatsappGatewayService whatsappGatewayService;

    @Override
    public void run(ApplicationArguments args) {
        bootstrapService.ensureStartedOnApplicationBoot();
        if (!bootstrapProperties.isAutoInitClient()) {
            return;
        }

        try {
            ApiResponse<WhatsappStatusData> status = whatsappGatewayService.getStatus();
            if (status.getData() == null || !whatsappGatewayService.isReady(status)) {
                whatsappGatewayService.initClient();
            }
        } catch (Exception ex) {
            log.warn("Bootstrap WhatsApp auto init dilewati: {}", ex.getMessage());
        }
    }
}
