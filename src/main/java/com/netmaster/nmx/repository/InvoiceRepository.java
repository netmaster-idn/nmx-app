package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.Invoice;
import com.netmaster.nmx.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    @Override
    @EntityGraph(attributePaths = {"customer", "customerService"})
    List<Invoice> findAll();

    @EntityGraph(attributePaths = {
            "customer",
            "customerService",
            "customerService.odp",
            "customerService.odp.companyProfile",
            "companyProfile",
            "paymentMethodEntity",
            "bankAccount",
            "bankAccount.paymentMethod"
    })
    @Query("SELECT i FROM Invoice i WHERE i.id = :id")
    Optional<Invoice> findDocumentById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"customer", "customerService"})
    @Query("SELECT i FROM Invoice i WHERE i.customer.id IN :customerIds")
    List<Invoice> findByCustomerIdIn(@Param("customerIds") Collection<Long> customerIds);

    @Query("SELECT i FROM Invoice i WHERE i.customer.id = :customerId ORDER BY i.billingMonth DESC")
    List<Invoice> findByCustomerIdOrderByBillingMonthDesc(@Param("customerId") Long customerId);

    @Query("SELECT i FROM Invoice i WHERE i.customer.id = :customerId AND i.status = :status ORDER BY i.billingMonth DESC")
    List<Invoice> findByCustomerIdAndStatus(@Param("customerId") Long customerId, @Param("status") String status);

    @Query("SELECT i FROM Invoice i WHERE i.status = 'pending' AND i.dueDate < :date")
    List<Invoice> findOverdueInvoices(@Param("date") LocalDate date);

    @Query("SELECT i FROM Invoice i WHERE i.customer.customerCode = :customerCode AND i.billingMonth = :billingMonth")
    Optional<Invoice> findByCustomerCodeAndBillingMonth(@Param("customerCode") String customerCode, @Param("billingMonth") LocalDate billingMonth);

    Optional<Invoice> findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc(String prefix);

    List<Invoice> findByCustomer(Customer customer);
    void deleteByCustomerId(Long customerId);

    @Query("SELECT i FROM Invoice i WHERE i.status = :status ORDER BY i.dueDate ASC")
    List<Invoice> findByStatus(@Param("status") String status);

    List<Invoice> findByStatusOrderByDueDateAsc(String status);

    @EntityGraph(attributePaths = {"customer", "customerService"})
    @Query("""
            SELECT i
            FROM Invoice i
            WHERE i.dueDate = :dueDate
              AND LOWER(COALESCE(i.status, 'pending')) NOT IN ('paid', 'cancelled', 'no_payment')
            ORDER BY i.dueDate ASC, i.id ASC
            """)
    List<Invoice> findWhatsappReminderCandidates(@Param("dueDate") LocalDate dueDate);

    @Query("SELECT i FROM Invoice i WHERE i.billingMonth BETWEEN :startDate AND :endDate ORDER BY i.billingMonth DESC")
    List<Invoice> findByBillingMonthBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT i FROM Invoice i WHERE i.customer.id = :customerId AND FUNCTION('YEAR', i.billingMonth) = :year AND FUNCTION('MONTH', i.billingMonth) = :month ORDER BY i.billingMonth DESC")
    List<Invoice> findByCustomerIdAndYearAndMonth(@Param("customerId") Long customerId, @Param("year") Integer year, @Param("month") Integer month);

    @Query("SELECT i FROM Invoice i WHERE i.customer.id = :customerId AND FUNCTION('YEAR', i.billingMonth) = :year ORDER BY i.billingMonth DESC")
    List<Invoice> findByCustomerIdAndYear(@Param("customerId") Long customerId, @Param("year") Integer year);

    @Query("SELECT i FROM Invoice i WHERE i.customer.id = :customerId AND i.status = 'paid' ORDER BY i.paymentDate DESC")
    List<Invoice> findPaidInvoicesByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT DISTINCT FUNCTION('YEAR', COALESCE(i.billingMonth, i.dueDate, i.paymentDate)) FROM Invoice i WHERE i.customer.id = :customerId AND COALESCE(i.billingMonth, i.dueDate, i.paymentDate) IS NOT NULL ORDER BY FUNCTION('YEAR', COALESCE(i.billingMonth, i.dueDate, i.paymentDate)) DESC")
    List<Integer> findDistinctYearsByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT DISTINCT FUNCTION('MONTH', COALESCE(i.billingMonth, i.dueDate, i.paymentDate)) FROM Invoice i WHERE i.customer.id = :customerId AND FUNCTION('YEAR', COALESCE(i.billingMonth, i.dueDate, i.paymentDate)) = :year AND COALESCE(i.billingMonth, i.dueDate, i.paymentDate) IS NOT NULL ORDER BY FUNCTION('MONTH', COALESCE(i.billingMonth, i.dueDate, i.paymentDate)) DESC")
    List<Integer> findDistinctMonthsByCustomerIdAndYear(@Param("customerId") Long customerId, @Param("year") Integer year);

    @Query("SELECT DISTINCT FUNCTION('YEAR', COALESCE(i.billingMonth, i.dueDate, i.paymentDate)) FROM Invoice i WHERE COALESCE(i.billingMonth, i.dueDate, i.paymentDate) IS NOT NULL ORDER BY FUNCTION('YEAR', COALESCE(i.billingMonth, i.dueDate, i.paymentDate)) DESC")
    List<Integer> findDistinctYears();

    @Query("SELECT DISTINCT FUNCTION('MONTH', COALESCE(i.billingMonth, i.dueDate, i.paymentDate)) FROM Invoice i WHERE FUNCTION('YEAR', COALESCE(i.billingMonth, i.dueDate, i.paymentDate)) = :year AND COALESCE(i.billingMonth, i.dueDate, i.paymentDate) IS NOT NULL ORDER BY FUNCTION('MONTH', COALESCE(i.billingMonth, i.dueDate, i.paymentDate)) ASC")
    List<Integer> findDistinctMonthsByYear(@Param("year") Integer year);

    @Query("""
            SELECT DISTINCT i.customer.id
            FROM Invoice i
            WHERE i.customer.id IS NOT NULL
              AND LOWER(i.status) IN ('pending', 'partial')
              AND (
                i.dueDate IS NULL
                OR (
                  i.dueDate >= :today
                  AND i.dueDate <= :reminderDate
                )
              )
            """)
    List<Long> findCustomerIdsByBelumLunas(@Param("today") LocalDate today, @Param("reminderDate") LocalDate reminderDate);

    @Query("""
            SELECT DISTINCT i.customer.id
            FROM Invoice i
            WHERE i.customer.id IS NOT NULL
              AND LOWER(i.status) = 'paid'
            """)
    List<Long> findCustomerIdsByLunas();

    @Query("""
            SELECT DISTINCT i.customer.id
            FROM Invoice i
            WHERE i.customer.id IS NOT NULL
              AND LOWER(i.status) NOT IN ('paid', 'cancelled', 'no_payment')
            """)
    List<Long> findCustomerIdsWithOutstanding();

    @Query("""
            SELECT DISTINCT i.customer.id
            FROM Invoice i
            WHERE i.customer.id IS NOT NULL
              AND (
                LOWER(i.status) = 'overdue'
                OR (
                  i.dueDate IS NOT NULL
                  AND i.dueDate < :today
                  AND LOWER(i.status) NOT IN ('paid', 'cancelled', 'no_payment')
                )
              )
            """)
    List<Long> findCustomerIdsByJatuhTempo(@Param("today") LocalDate today);
}

