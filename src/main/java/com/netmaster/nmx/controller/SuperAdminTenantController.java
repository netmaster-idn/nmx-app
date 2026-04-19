package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.superadmin.*;
import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.service.SuperAdminTenantAccessService;
import com.netmaster.nmx.service.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.netmaster.nmx.security.SessionAttributeKeys.SUPERADMIN_ID;

@RestController
@RequiredArgsConstructor
public class SuperAdminTenantController {

    private final SuperAdminTenantAccessService tenantAccessService;
    private final TenantService tenantService;

    @GetMapping("/superadmin/tenants")
    public ResponseEntity<ApiResponse<List<TenantDirectoryResponse>>> getTenants() {
        return ResponseEntity.ok(ApiResponse.success("Daftar tenant berhasil diambil.", tenantAccessService.findAllTenants()));
    }

    @GetMapping("/superadmin/tenants/pending")
    public ResponseEntity<ApiResponse<List<Tenant>>> getPendingTenants() {
        return ResponseEntity.ok(ApiResponse.success(
                "Daftar tenant pending berhasil diambil.",
                tenantService.getPendingTenants()
        ));
    }

    @PostMapping("/superadmin/tenants/{id}/approve")
    public ResponseEntity<ApiResponse<Tenant>> approve(@PathVariable Long id,
                                                       @Valid @RequestBody(required = false) TenantApprovalRequest request,
                                                       HttpSession session,
                                                       HttpServletRequest httpServletRequest) {
        Tenant tenant = tenantService.approveTenant(
                id,
                (Long) session.getAttribute(SUPERADMIN_ID),
                httpServletRequest.getRemoteAddr()
        );
        return ResponseEntity.ok(ApiResponse.success("Tenant berhasil di-approve dan diprovision.", tenant));
    }

    @PostMapping("/superadmin/tenants/{id}/reject")
    public ResponseEntity<ApiResponse<com.netmaster.nmx.master.model.IspRegistration>> reject(@PathVariable Long id,
                                                               @Valid @RequestBody TenantRejectRequest request,
                                                               HttpSession session,
                                                               HttpServletRequest httpServletRequest) {
        com.netmaster.nmx.master.model.IspRegistration registration = tenantService.rejectTenant(
                id,
                (Long) session.getAttribute(SUPERADMIN_ID),
                request.getReason(),
                httpServletRequest.getRemoteAddr()
        );
        return ResponseEntity.ok(ApiResponse.success("Tenant registration berhasil ditolak.", registration));
    }

    @GetMapping("/superadmin/tenants/{id}")
    public ResponseEntity<ApiResponse<Tenant>> getTenant(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Detail tenant berhasil diambil.", tenantAccessService.getTenant(id)));
    }

    @PutMapping("/superadmin/tenants/{id}")
    public ResponseEntity<ApiResponse<Tenant>> updateTenant(@PathVariable Long id,
                                                            @Valid @RequestBody TenantUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Tenant berhasil diperbarui.", tenantAccessService.updateTenant(id, request)));
    }

    @DeleteMapping("/superadmin/tenants/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTenant(@PathVariable Long id) {
        tenantAccessService.softDeleteTenant(id);
        return ResponseEntity.ok(ApiResponse.success("Tenant berhasil di-soft-delete.", null));
    }

    @PostMapping("/superadmin/tenants/{id}/impersonate")
    public ResponseEntity<ApiResponse<Tenant>> impersonate(@PathVariable Long id,
                                                           HttpSession session,
                                                           HttpServletRequest httpServletRequest) {
        Tenant tenant = tenantAccessService.openSupportContext(
                id,
                session,
                (Long) session.getAttribute(SUPERADMIN_ID),
                httpServletRequest.getRemoteAddr()
        );
        return ResponseEntity.ok(ApiResponse.success("Support context tenant berhasil dibuka dalam mode read-only.", tenant));
    }

    @GetMapping("/superadmin/tenants/{id}/summary")
    public ResponseEntity<ApiResponse<TenantSummaryResponse>> summary(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Ringkasan tenant berhasil diambil.", tenantAccessService.getSummary(id)));
    }
}
