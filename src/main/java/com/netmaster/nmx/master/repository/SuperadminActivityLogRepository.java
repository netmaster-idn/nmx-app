package com.netmaster.nmx.master.repository;

import com.netmaster.nmx.master.model.SuperadminActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuperadminActivityLogRepository extends JpaRepository<SuperadminActivityLog, Long> {
    List<SuperadminActivityLog> findTop10ByOrderByCreatedAtDesc();
}
