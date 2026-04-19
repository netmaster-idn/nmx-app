package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.AcsSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AcsSettingsRepository extends JpaRepository<AcsSettings, Long> {
}
