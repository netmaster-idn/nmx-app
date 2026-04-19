package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.CustomerBillingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerBillingStatusRepository extends JpaRepository<CustomerBillingStatus, Long> {

    Optional<CustomerBillingStatus> findByCustomerId(Long customerId);

    List<CustomerBillingStatus> findByCustomerIdIn(Collection<Long> customerIds);
}
