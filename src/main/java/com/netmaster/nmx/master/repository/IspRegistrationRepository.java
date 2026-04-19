package com.netmaster.nmx.master.repository;

import com.netmaster.nmx.master.model.IspRegistration;
import com.netmaster.nmx.master.model.TenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IspRegistrationRepository extends JpaRepository<IspRegistration, Long> {
    Optional<IspRegistration> findBySlug(String slug);
    Optional<IspRegistration> findByEmail(String email);
    Optional<IspRegistration> findByOwnerUsernameIgnoreCase(String ownerUsername);
    List<IspRegistration> findByStatusOrderByCreatedAtAsc(TenantStatus status);
}
