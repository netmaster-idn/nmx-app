package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.MonitoringLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonitoringLogRepository extends JpaRepository<MonitoringLog, Long> {
}
