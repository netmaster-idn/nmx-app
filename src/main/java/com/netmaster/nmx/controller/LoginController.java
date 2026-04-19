package com.netmaster.nmx.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(Model model,
                        CsrfToken csrfToken,
                        Authentication authentication,
                        @RequestParam(value = "tenantRequired", required = false) String tenantRequired,
                        HttpServletRequest request,
                        HttpServletResponse response) {
        boolean requireTenantReauth = "1".equals(tenantRequired);
        if (requireTenantReauth && isRealAuthenticated(authentication)) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            authentication = null;
        }

        if (isRealAuthenticated(authentication)) {
            boolean isSuperAdmin = authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
            return isSuperAdmin ? "redirect:/superadmin/dashboard" : "redirect:/dashboard";
        }

        model.addAttribute("tenantRequired", requireTenantReauth);
        if (csrfToken != null) {
            model.addAttribute("_csrf", csrfToken);
            model.addAttribute("csrfParameterName", csrfToken.getParameterName());
            model.addAttribute("csrfTokenValue", csrfToken.getToken());
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerRedirect() {
        return "redirect:/register.html";
    }

    private boolean isRealAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

}
