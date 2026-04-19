package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.OntActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OntActionLogRepository extends JpaRepository<OntActionLog, Long> {
}
