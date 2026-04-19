package com.netmaster.nmx.security;

import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantResolverTest {

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private TenantResolver tenantResolver;

    @Test
    void shouldResolveTenantFromHeader() {
        Tenant tenant = new Tenant();
        tenant.setId(7L);
        tenant.setSlug("isp-a");
        when(tenantRepository.findBySlug("isp-a")).thenReturn(Optional.of(tenant));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Slug", "isp-a");

        Optional<Tenant> resolved = tenantResolver.resolve(request);

        assertThat(resolved).isPresent();
        assertThat(resolved.get().getId()).isEqualTo(7L);
    }
}
