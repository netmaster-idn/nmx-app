package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByKtpNumber(String ktpNumber);
    Optional<Customer> findByPhone(String phone);
    Optional<Customer> findByEmail(String email);
    Optional<Customer> findByCustomerCode(String customerCode);
    Optional<Customer> findTopByCustomerCodeStartingWithOrderByCustomerCodeDesc(String prefix);
    List<Customer> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
    
    @Query("SELECT c FROM Customer c WHERE c.fullName LIKE %:name% OR c.phone LIKE %:phone% OR c.customerCode LIKE %:code%")
    List<Customer> searchCustomers(@Param("name") String name, @Param("phone") String phone, @Param("code") String code);
    
    @Query("SELECT MAX(CAST(SUBSTRING(c.customerCode FROM 10 FOR 3) AS int)) FROM Customer c WHERE c.customerCode LIKE :prefix%")
    Integer findMaxCustomerCodeSequence(@Param("prefix") String prefix);

    @Modifying
    @Query("UPDATE Customer c SET c.status = 'pending'")
    int resetAllCustomerStatusesToPending();
}

