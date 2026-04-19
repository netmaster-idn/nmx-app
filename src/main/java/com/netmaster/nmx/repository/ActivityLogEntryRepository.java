package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.ActivityLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityLogEntryRepository extends JpaRepository<ActivityLogEntry, Long> {
}
