package com.netmaster.nmx.master.repository;

import com.netmaster.nmx.master.model.MasterAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MasterAuditLogRepository extends JpaRepository<MasterAuditLog, Long> {
}
