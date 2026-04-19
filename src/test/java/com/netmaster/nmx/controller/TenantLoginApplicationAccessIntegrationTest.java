package com.netmaster.nmx.controller;

import com.netmaster.nmx.model.Role;
import com.netmaster.nmx.model.User;
import com.netmaster.nmx.service.TenantAuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static com.netmaster.nmx.security.SessionAttributeKeys.TENANT_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TenantLoginApplicationAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantAuthenticationService tenantAuthenticationService;

    @Test
    void tenantLogin_buildsSecurityContextForMainApplicationRoutes() throws Exception {
        User tenantUser = new User();
        tenantUser.setId(15L);
        tenantUser.setUsername("habibi");
        tenantUser.setPassword("$2a$10$tenant-password-hash");
        tenantUser.setFullName("Habibi Tenant");
        tenantUser.setActive(true);

        Role tenantRole = new Role();
        tenantRole.setName("ROLE_TENANT_ADMIN");
        tenantRole.setPermissionsLevel("FULL");
        tenantUser.setRoles(Set.of(tenantRole));

        doAnswer(invocation -> {
            jakarta.servlet.http.HttpSession session = invocation.getArgument(3, jakarta.servlet.http.HttpSession.class);
            session.setAttribute(TENANT_ID, 77L);
            return tenantUser;
        }).when(tenantAuthenticationService).login(eq("wifi-bersama"), eq("habibi"), eq("secret"), any());

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/tenant/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantSlug": "wifi-bersama",
                                  "username": "habibi",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn()
                .getRequest()
                .getSession(false);

        org.assertj.core.api.Assertions.assertThat(session).isNotNull();
        org.assertj.core.api.Assertions.assertThat(session.getAttribute(SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
        org.assertj.core.api.Assertions.assertThat(session.getAttribute(TENANT_ID)).isEqualTo(77L);

        mockMvc.perform(get("/monitoring/alert").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/system/tenant-approval").session(session))
                .andExpect(status().isForbidden());
    }
}
