package com.netmaster.nmx.service;

import com.netmaster.nmx.dto.tenant.RegisterTenantRequest;
import com.netmaster.nmx.master.model.IspRegistration;
import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.model.TenantStatus;
import com.netmaster.nmx.master.repository.ApprovalHistoryRepository;
import com.netmaster.nmx.master.repository.IspRegistrationRepository;
import com.netmaster.nmx.master.repository.TenantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantRegistrationServiceTest {

    @Mock
    private IspRegistrationRepository registrationRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private ApprovalHistoryRepository approvalHistoryRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MasterAuditLogService auditLogService;

    @InjectMocks
    private TenantRegistrationService tenantRegistrationService;

    @Test
    void register_shouldNotDowngradeExistingApprovedTenantToPending() {
        RegisterTenantRequest request = new RegisterTenantRequest();
        request.setCompanyName("Fiber Kita");
        request.setEmail("tenant@example.com");
        request.setPhone("08123456789");
        request.setAddress("Jl. Mawar");
        request.setOwnerName("Owner");
        request.setOwnerEmail("owner@example.com");
        request.setOwnerUsername("owner");
        request.setPassword("secret");
        request.setRequestedPlanCode("TRIAL");

        Tenant existingTenant = new Tenant();
        existingTenant.setId(99L);
        existingTenant.setRegistrationId(7L);
        existingTenant.setStatus(TenantStatus.ACTIVE);

        when(registrationRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(registrationRepository.findBySlug("fiber-kita")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(registrationRepository.save(any(IspRegistration.class))).thenAnswer(invocation -> {
            IspRegistration registration = invocation.getArgument(0);
            registration.setId(7L);
            return registration;
        });
        when(tenantRepository.findByRegistrationId(7L)).thenReturn(Optional.of(existingTenant));

        tenantRegistrationService.register(request, "127.0.0.1");

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantRepository).save(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().getStatus()).isEqualTo(TenantStatus.ACTIVE);
        verify(approvalHistoryRepository, times(1)).save(any());
    }
}
