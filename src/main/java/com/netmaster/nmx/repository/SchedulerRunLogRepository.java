package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.SchedulerRunLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SchedulerRunLogRepository extends JpaRepository<SchedulerRunLog, Long> {

    Optional<SchedulerRunLog> findTopByJobNameOrderByStartedAtDesc(String jobName);

    Optional<SchedulerRunLog> findTopByOrderByStartedAtDesc();

    List<SchedulerRunLog> findTop50ByOrderByStartedAtDesc();
}
