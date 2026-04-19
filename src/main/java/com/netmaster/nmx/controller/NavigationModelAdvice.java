package com.netmaster.nmx.controller;

import com.netmaster.nmx.master.model.TenantStatus;
import com.netmaster.nmx.master.repository.IspRegistrationRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class NavigationModelAdvice {

    private final IspRegistrationRepository ispRegistrationRepository;

    @ModelAttribute("superadminPendingTenantCount")
    public long superadminPendingTenantCount(Authentication authentication) {
        if (!isSuperAdmin(authentication)) {
            return 0;
        }
        try {
            return ispRegistrationRepository.findByStatusOrderByCreatedAtAsc(TenantStatus.PENDING).size();
        } catch (DataAccessException ex) {
            log.warn("Unable to load pending tenant count from master registration table. Falling back to 0.", ex);
            return 0;
        }
    }

    @ModelAttribute("resolvedTenant")
    public Object resolvedTenant(HttpServletRequest request) {
        return request.getAttribute("resolvedTenant");
    }

    @ModelAttribute("supportReadOnlyMode")
    public boolean supportReadOnlyMode(HttpServletRequest request) {
        Object value = request.getSession(false) != null
                ? request.getSession(false).getAttribute(com.netmaster.nmx.security.SessionAttributeKeys.SUPPORT_READ_ONLY)
                : null;
        return Boolean.TRUE.equals(value);
    }

    private boolean isSuperAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_SUPER_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
