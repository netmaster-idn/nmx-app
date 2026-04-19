package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.BillingAutomationSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BillingAutomationSettingRepository extends JpaRepository<BillingAutomationSetting, Long> {

    Optional<BillingAutomationSetting> findFirstByIsActiveTrueOrderByIdAsc();
}
