package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {
    // Management view (active + inactive)
    List<ServiceType> findAllByOrderByNameAsc();

    // Customer-facing dropdowns (registration, etc.)
    List<ServiceType> findByIsActiveTrueOrderByNameAsc();

    Optional<ServiceType> findByNameIgnoreCase(String name);
}
