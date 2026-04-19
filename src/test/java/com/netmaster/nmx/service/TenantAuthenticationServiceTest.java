package com.netmaster.nmx.service;

import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.model.TenantStatus;
import com.netmaster.nmx.master.repository.TenantRepository;
import com.netmaster.nmx.model.User;
import com.netmaster.nmx.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantAuthenticationServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantConnectionManager tenantConnectionManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private HttpSession session;

    @InjectMocks
    private TenantAuthenticationService tenantAuthenticationService;

    @Test
    void shouldRejectPendingTenantLogin() {
        Tenant tenant = new Tenant();
        tenant.setSlug("pending-isp");
        tenant.setStatus(TenantStatus.PENDING);
        when(tenantRepository.findBySlug("pending-isp")).thenReturn(Optional.of(tenant));

        assertThatThrownBy(() -> tenantAuthenticationService.login("pending-isp", "admin", "secret", session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tenant belum aktif");
    }
}
