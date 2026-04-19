package com.netmaster.nmx.master.repository;

import com.netmaster.nmx.master.model.ApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistory, Long> {
    List<ApprovalHistory> findByRegistrationIdOrderByCreatedAtAsc(Long registrationId);
}
