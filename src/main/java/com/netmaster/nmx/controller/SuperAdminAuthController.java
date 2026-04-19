package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.superadmin.SuperAdminLoginRequest;
import com.netmaster.nmx.master.model.SuperAdmin;
import com.netmaster.nmx.service.MasterSuperAdminAuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SuperAdminAuthController {

    private final MasterSuperAdminAuthService authService;

    @PostMapping("/superadmin/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@Valid @RequestBody SuperAdminLoginRequest request,
                                                                  HttpSession session) {
        SuperAdmin admin = authService.login(request.getUsername(), request.getPassword(), session);
        establishSecuritySession(admin, session);
        return ResponseEntity.ok(ApiResponse.success("Login superadmin berhasil.", Map.of(
                "id", admin.getId(),
                "username", admin.getUsername(),
                "fullName", admin.getFullName()
        )));
    }

    private void establishSecuritySession(SuperAdmin admin, HttpSession session) {
        Set<SimpleGrantedAuthority> authorities = Set.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        UserDetails principal = new org.springframework.security.core.userdetails.User(
                admin.getUsername(),
                admin.getPasswordHash(),
                Boolean.TRUE.equals(admin.getActive()),
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
}
