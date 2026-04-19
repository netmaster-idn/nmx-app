package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.superadmin.TenantDirectoryResponse;
import com.netmaster.nmx.dto.superadmin.TenantApprovalRequest;
import com.netmaster.nmx.dto.superadmin.TenantRejectRequest;
import com.netmaster.nmx.master.model.IspRegistration;
import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.master.model.TenantStatus;
import com.netmaster.nmx.master.repository.IspRegistrationRepository;
import com.netmaster.nmx.master.repository.SuperAdminRepository;
import com.netmaster.nmx.service.SuperAdminTenantAccessService;
import com.netmaster.nmx.service.TenantApprovalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
@Controller
@RequiredArgsConstructor
@Slf4j
public class SuperAdminApprovalPanelController {

    private final IspRegistrationRepository registrationRepository;
    private final TenantApprovalService tenantApprovalService;
    private final SuperAdminTenantAccessService tenantAccessService;
    private final SuperAdminRepository superAdminRepository;

    @GetMapping("/system/tenant-approval")
    public String pendingApprovalPanel(Model model) {
        List<IspRegistration> pendingRegistrations = loadPendingRegistrationsSafely();
        model.addAttribute("pendingRegistrations", pendingRegistrations);
        model.addAttribute("pendingRegistrationsCount", pendingRegistrations.size());
        model.addAttribute("page", "tenant-approval");
        return "layout/base";
    }

    @GetMapping("/system/tenant-directory")
    public String tenantDirectory(@RequestParam(name = "restored", required = false) String restored,
                                  Model model) {
        List<TenantDirectoryResponse> tenants = tenantAccessService.findAllTenants();
        List<TenantDirectoryResponse> deletedTenants = tenantAccessService.findDeletedTenants();
        long activeCount = tenants.stream()
                .filter(tenant -> "ACTIVE".equalsIgnoreCase(tenant.getStatus()))
                .count();
        long pendingCount = tenants.stream()
                .filter(tenant -> "PENDING".equalsIgnoreCase(tenant.getStatus()))
                .count();
        long suspendedCount = tenants.stream()
                .filter(tenant -> "SUSPENDED".equalsIgnoreCase(tenant.getStatus()))
                .count();
        long expiringSoonCount = tenants.stream()
                .filter(tenant -> "ACTIVE".equalsIgnoreCase(tenant.getStatus()))
                .filter(tenant -> tenant.getSubscriptionEndsAt() != null)
                .filter(tenant -> !tenant.getSubscriptionEndsAt().isBefore(java.time.LocalDate.now())
                        && !tenant.getSubscriptionEndsAt().isAfter(java.time.LocalDate.now().plusDays(14)))
                .count();

        model.addAttribute("tenantDirectory", tenants);
        model.addAttribute("tenantDirectoryCount", tenants.size());
        model.addAttribute("tenantDirectoryActiveCount", activeCount);
        model.addAttribute("tenantDirectoryPendingCount", pendingCount);
        model.addAttribute("tenantDirectorySuspendedCount", suspendedCount);
        model.addAttribute("tenantDirectoryExpiringSoonCount", expiringSoonCount);
        model.addAttribute("tenantDirectoryDeleted", deletedTenants);
        model.addAttribute("tenantDirectoryDeletedCount", deletedTenants.size());
        if (restored != null) {
            model.addAttribute("successMessage", "Tenant dari recycle bin berhasil direstore.");
        }
        model.addAttribute("page", "tenant-directory");
        return "layout/base";
    }

    @PostMapping("/system/tenant-directory/{id}/restore")
    public String restoreTenant(@PathVariable Long id,
                                Authentication authentication,
                                HttpServletRequest httpServletRequest) {
        Long actorId = resolveCurrentUserId(authentication);
        tenantAccessService.restoreTenant(id, actorId, httpServletRequest.getRemoteAddr());
        return "redirect:/system/tenant-directory?restored=1";
    }

    @PostMapping("/system/tenant-approval/{id}/approve")
    @ResponseBody
    public ResponseEntity<ApiResponse<Tenant>> approve(@PathVariable Long id,
                                                       @Valid @RequestBody(required = false) TenantApprovalRequest request,
                                                       Authentication authentication,
                                                       HttpServletRequest httpServletRequest) {
        Long actorId = resolveCurrentUserId(authentication);
        Tenant tenant = tenantApprovalService.approve(
                id,
                actorId,
                request == null ? new TenantApprovalRequest() : request,
                httpServletRequest.getRemoteAddr()
        );
        return ResponseEntity.ok(ApiResponse.success("Tenant berhasil di-approve.", tenant));
    }

    @PostMapping("/system/tenant-approval/{id}/reject")
    @ResponseBody
    public ResponseEntity<ApiResponse<IspRegistration>> reject(@PathVariable Long id,
                                                               @Valid @RequestBody TenantRejectRequest request,
                                                               Authentication authentication,
                                                               HttpServletRequest httpServletRequest) {
        Long actorId = resolveCurrentUserId(authentication);
        IspRegistration registration = tenantApprovalService.reject(
                id,
                actorId,
                request,
                httpServletRequest.getRemoteAddr()
        );
        return ResponseEntity.ok(ApiResponse.success("Registrasi tenant berhasil ditolak.", registration));
    }

    private Long resolveCurrentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autentikasi superadmin tidak ditemukan.");
        }
        return superAdminRepository.findByUsername(authentication.getName())
                .map(admin -> admin.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User superadmin tidak ditemukan."));
    }

    private List<IspRegistration> loadPendingRegistrationsSafely() {
        try {
            return registrationRepository.findByStatusOrderByCreatedAtAsc(TenantStatus.PENDING);
        } catch (DataAccessException ex) {
            log.warn("Unable to load pending tenant registrations from master registration table. Falling back to an empty list.", ex);
            return List.of();
        }
    }
}
