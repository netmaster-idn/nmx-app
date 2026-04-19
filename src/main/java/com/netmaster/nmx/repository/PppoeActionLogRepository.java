package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.PppoeActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PppoeActionLogRepository extends JpaRepository<PppoeActionLog, Long> {

    List<PppoeActionLog> findTop200ByOrderByCreatedAtDesc();

    List<PppoeActionLog> findByCustomerIdInOrderByCreatedAtDesc(Collection<Long> customerIds);

    Optional<PppoeActionLog> findTopByCustomerIdAndActionTypeAndStatusIgnoreCaseOrderByExecutedAtDesc(Long customerId,
                                                                                                       String actionType,
                                                                                                       String status);
}
