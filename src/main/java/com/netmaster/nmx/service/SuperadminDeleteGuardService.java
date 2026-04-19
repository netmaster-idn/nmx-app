package com.netmaster.nmx.service;

import com.netmaster.nmx.model.User;
import com.netmaster.nmx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SuperadminDeleteGuardService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public boolean isPasswordValid(String rawPassword, Authentication auth) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return false;
        }

        if (auth != null && hasSuperadminRole(auth)) {
            Optional<User> currentUser = userRepository.findByUsername(auth.getName());
            if (currentUser.isPresent() && passwordEncoder.matches(rawPassword, currentUser.get().getPassword())) {
                return true;
            }
        }

        return userRepository.findAll().stream()
                .filter(User::isActive)
                .filter(this::hasTenantSuperAdminRole)
                .anyMatch(user -> passwordEncoder.matches(rawPassword, user.getPassword()));
    }

    public boolean isTenantAdminPasswordValid(String rawPassword, Authentication auth) {
        if (rawPassword == null || rawPassword.isBlank() || auth == null || auth.getName() == null) {
            return false;
        }
        if (!hasTenantAdminRole(auth)) {
            return false;
        }

        return userRepository.findByUsername(auth.getName())
                .filter(User::isActive)
                .filter(this::hasTenantAdminRole)
                .map(user -> passwordEncoder.matches(rawPassword, user.getPassword()))
                .orElse(false);
    }

    private boolean hasSuperadminRole(Authentication auth) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if ("ROLE_SUPER_ADMIN".equals(authority.getAuthority())
                    || "ROLE_TENANT_SUPER_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTenantAdminRole(Authentication auth) {
        for (GrantedAuthority authority : auth.getAuthorities()) {
            String roleName = authority.getAuthority();
            if ("ROLE_ADMIN".equals(roleName)
                    || "ROLE_TENANT_ADMIN".equals(roleName)
                    || "ROLE_TENANT_SUPER_ADMIN".equals(roleName)
                    || "ROLE_SUPER_ADMIN".equals(roleName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTenantSuperAdminRole(User user) {
        if (user == null || user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream()
                .anyMatch(role -> "ROLE_SUPER_ADMIN".equals(role.getName())
                        || "ROLE_TENANT_SUPER_ADMIN".equals(role.getName()));
    }

    private boolean hasTenantAdminRole(User user) {
        if (user == null || user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream()
                .anyMatch(role -> "ROLE_ADMIN".equals(role.getName())
                        || "ROLE_TENANT_ADMIN".equals(role.getName())
                        || "ROLE_TENANT_SUPER_ADMIN".equals(role.getName())
                        || "ROLE_SUPER_ADMIN".equals(role.getName()));
    }
}
