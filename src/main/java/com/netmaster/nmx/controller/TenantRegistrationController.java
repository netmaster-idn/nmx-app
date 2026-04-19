package com.netmaster.nmx.controller;

import com.netmaster.nmx.config.TenantProvisioningProperties;
import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.tenant.RegisterTenantRequest;
import com.netmaster.nmx.master.model.IspRegistration;
import com.netmaster.nmx.service.TenantRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TenantRegistrationController {

    private final TenantProvisioningProperties tenantProvisioningProperties;
    private final TenantRegistrationService tenantRegistrationService;

    @PostMapping("/register-tenant")
    public ResponseEntity<ApiResponse<IspRegistration>> registerTenant(@Valid @RequestBody RegisterTenantRequest request,
                                                                       HttpServletRequest httpServletRequest) {
        if (!tenantProvisioningProperties.isEnabled()) {
            return ResponseEntity.status(503).body(ApiResponse.error("Fitur tenant dinonaktifkan sementara."));
        }
        IspRegistration registration = tenantRegistrationService.register(request, httpServletRequest.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(
                "Registrasi tenant diterima. Status saat ini PENDING dan menunggu approval superadmin.",
                registration
        ));
    }
}
