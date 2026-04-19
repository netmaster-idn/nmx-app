package com.netmaster.nmx.service;

import com.netmaster.nmx.master.model.SuperAdmin;
import com.netmaster.nmx.master.repository.SuperAdminRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static com.netmaster.nmx.security.SessionAttributeKeys.SUPERADMIN_ID;
import static com.netmaster.nmx.security.SessionAttributeKeys.SUPERADMIN_USERNAME;

@Service
@RequiredArgsConstructor
public class MasterSuperAdminAuthService {

    private final SuperAdminRepository superAdminRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional("masterTransactionManager")
    public SuperAdmin login(String username, String password, HttpSession session) {
        SuperAdmin superAdmin = superAdminRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Superadmin tidak ditemukan."));

        if (!Boolean.TRUE.equals(superAdmin.getActive())) {
            throw new IllegalStateException("Akun superadmin tidak aktif.");
        }
        if (!passwordEncoder.matches(password, superAdmin.getPasswordHash())) {
            throw new IllegalArgumentException("Username atau password salah.");
        }

        superAdmin.setLastLoginAt(LocalDateTime.now());
        superAdminRepository.save(superAdmin);
        session.setAttribute(SUPERADMIN_ID, superAdmin.getId());
        session.setAttribute(SUPERADMIN_USERNAME, superAdmin.getUsername());
        return superAdmin;
    }
}
