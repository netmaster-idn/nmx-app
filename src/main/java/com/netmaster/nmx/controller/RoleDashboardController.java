package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.superadmin.SuperAdminDashboardResponse;
import com.netmaster.nmx.service.SuperAdminDashboardService;
import com.netmaster.nmx.service.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import static com.netmaster.nmx.security.SessionAttributeKeys.SUPERADMIN_ID;
import static com.netmaster.nmx.security.SessionAttributeKeys.TENANT_SLUG;

@Controller
@RequiredArgsConstructor
public class RoleDashboardController {

    private final SuperAdminDashboardService superAdminDashboardService;
    private final TenantService tenantService;

    @GetMapping("/superadmin/dashboard")
    public String superAdminDashboard(Model model) {
        SuperAdminDashboardResponse dashboard = superAdminDashboardService.buildDashboard();
        model.addAttribute("superadminDashboard", dashboard);
        model.addAttribute("page", "superadmin-dashboard");
        return "layout/base";
    }

    @GetMapping("/tenant/dashboard")
    public String tenantDashboard(HttpSession session) {
        Object tenantSlug = session.getAttribute(TENANT_SLUG);
        if (tenantSlug instanceof String slug && !slug.isBlank()) {
            return "redirect:/dashboard?tenantSlug=" + slug.trim();
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/")
    public String root(Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()))) {
            return "redirect:/superadmin/dashboard";
        }
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    @PostMapping("/superadmin/dashboard/tenants/{id}/approve")
    public String approveTenant(@PathVariable Long id,
                                HttpSession session,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        tenantService.approveTenant(id, (Long) session.getAttribute(SUPERADMIN_ID), request.getRemoteAddr());
        redirectAttributes.addFlashAttribute("successMessage", "Tenant berhasil di-approve.");
        return "redirect:/superadmin/dashboard";
    }

    @PostMapping("/superadmin/dashboard/tenants/{id}/reject")
    public String rejectTenant(@PathVariable Long id,
                               HttpSession session,
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes) {
        tenantService.rejectTenant(id, (Long) session.getAttribute(SUPERADMIN_ID), "Rejected from control plane queue", request.getRemoteAddr());
        redirectAttributes.addFlashAttribute("successMessage", "Tenant berhasil ditolak.");
        return "redirect:/superadmin/dashboard";
    }

    @PostMapping("/superadmin/dashboard/tenants/{id}/suspend")
    public String suspendTenant(@PathVariable Long id,
                                HttpSession session,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        tenantService.suspendTenant(id, (Long) session.getAttribute(SUPERADMIN_ID), request.getRemoteAddr());
        redirectAttributes.addFlashAttribute("successMessage", "Tenant berhasil disuspend.");
        return "redirect:/superadmin/dashboard";
    }

    @PostMapping("/superadmin/dashboard/tenants/{id}/activate")
    public String activateTenant(@PathVariable Long id,
                                 HttpSession session,
                                 HttpServletRequest request,
                                 RedirectAttributes redirectAttributes) {
        tenantService.activateTenant(id, (Long) session.getAttribute(SUPERADMIN_ID), request.getRemoteAddr());
        redirectAttributes.addFlashAttribute("successMessage", "Tenant berhasil diaktifkan.");
        return "redirect:/superadmin/dashboard";
    }
}
