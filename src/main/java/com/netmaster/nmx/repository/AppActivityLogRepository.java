package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.AppActivityLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppActivityLogRepository extends JpaRepository<AppActivityLog, Long> {
    List<AppActivityLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
