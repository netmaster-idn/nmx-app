package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.CompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, Long> {
    
    Optional<CompanyProfile> findByIsActiveTrue();

    Optional<CompanyProfile> findFirstByOrderByIdAsc();

    List<CompanyProfile> findByIsActiveTrueOrderByIdAsc();
}

