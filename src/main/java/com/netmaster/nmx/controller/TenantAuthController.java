package com.netmaster.nmx.controller;

import com.netmaster.nmx.config.TenantProvisioningProperties;
import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.tenant.TenantLoginRequest;
import com.netmaster.nmx.model.Role;
import com.netmaster.nmx.model.User;
import com.netmaster.nmx.service.TenantAuthenticationService;
import com.netmaster.nmx.service.WhatsappGatewayBootstrapService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.netmaster.nmx.security.SessionAttributeKeys.TENANT_ID;
import static com.netmaster.nmx.security.SessionAttributeKeys.TENANT_SLUG;

@RestController
@RequiredArgsConstructor
@Slf4j
public class TenantAuthController {

    private final TenantProvisioningProperties tenantProvisioningProperties;
    private final TenantAuthenticationService tenantAuthenticationService;
    private final WhatsappGatewayBootstrapService whatsappGatewayBootstrapService;

    @PostMapping("/tenant/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@Valid @RequestBody TenantLoginRequest request,
                                                                  HttpSession session) {
        if (!tenantProvisioningProperties.isEnabled()) {
            return ResponseEntity.status(503).body(ApiResponse.error("Fitur tenant dinonaktifkan sementara."));
        }
        User user = tenantAuthenticationService.login(request.getTenantSlug(), request.getUsername(), request.getPassword(), session);
        establishSecuritySession(user, session);
        triggerWhatsappGatewayBootstrapAfterLogin();
        return ResponseEntity.ok(ApiResponse.success("Login tenant berhasil.", Map.of(
                "tenantId", session.getAttribute(TENANT_ID),
                "tenantSlug", session.getAttribute(TENANT_SLUG),
                "username", user.getUsername(),
                "fullName", user.getFullName(),
                "active", user.isActive()
        )));
    }

    private void establishSecuritySession(User user, HttpSession session) {
        Set<GrantedAuthority> authorities = resolveAuthorities(user);
        UserDetails principal = new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.isActive(),
                true,
                true,
                true,
                authorities
        );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private Set<GrantedAuthority> resolveAuthorities(User user) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        if (user.getRoles() == null) {
            return authorities;
        }

        for (Role role : user.getRoles()) {
            if (role == null || role.getName() == null || role.getName().isBlank()) {
                continue;
            }

            authorities.add(new SimpleGrantedAuthority(role.getName()));

            String permissionLevel = role.getPermissionsLevel() == null ? "" : role.getPermissionsLevel().trim().toUpperCase();
            if ("FULL".equals(permissionLevel)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_TENANT_SUPER_ADMIN"));
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            } else if ("WRITE".equals(permissionLevel)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            } else if ("READ".equals(permissionLevel)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_SIDE_ADMIN"));
            }
        }

        return authorities.stream().collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void triggerWhatsappGatewayBootstrapAfterLogin() {
        try {
            whatsappGatewayBootstrapService.installAndStartAsync();
        } catch (Exception ex) {
            log.warn("Gagal memicu bootstrap WhatsApp gateway setelah login: {}", ex.getMessage());
        }
    }
}
