package com.netmaster.nmx.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HistoryPaymentPageRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void historyPage_rendersExternalJavascriptBootstrap() throws Exception {
        mockMvc.perform(get("/pelanggan/history")
                        .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/js/history-payment.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("dispatchHistoryQuickAction")));
    }

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor adminUser() {
        return user("integration-admin").authorities(() -> "ROLE_SUPER_ADMIN");
    }
}
