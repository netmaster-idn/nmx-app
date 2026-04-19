package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.CompanySetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanySettingRepository extends JpaRepository<CompanySetting, Long> {

    Optional<CompanySetting> findFirstByCompanyProfileIdAndIsActiveTrueOrderByIdAsc(Long companyProfileId);

    Optional<CompanySetting> findFirstByIsActiveTrueOrderByIdAsc();
}
