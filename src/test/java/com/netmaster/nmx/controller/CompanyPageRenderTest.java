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
class CompanyPageRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void companyPage_rendersDatabaseBackupActions() throws Exception {
        mockMvc.perform(get("/setting/company")
                        .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Export Database")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Import Database")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("exportDatabaseBackup")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("triggerDatabaseImport")));
    }

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor adminUser() {
        return user("integration-admin").authorities(() -> "ROLE_SUPER_ADMIN");
    }
}
