package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.AppErrorLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppErrorLogRepository extends JpaRepository<AppErrorLog, Long> {
    List<AppErrorLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
