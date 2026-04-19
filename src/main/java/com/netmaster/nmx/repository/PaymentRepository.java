package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.Payment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @EntityGraph(attributePaths = {"invoice", "customer"})
    List<Payment> findByInvoiceIdOrderByPaymentDateDescIdDesc(Long invoiceId);

    @EntityGraph(attributePaths = {"invoice", "customer"})
    List<Payment> findByCustomerIdOrderByPaymentDateDescIdDesc(Long customerId);

    @EntityGraph(attributePaths = {"invoice", "customer"})
    List<Payment> findByCustomerIdOrderByPaymentDateAscIdAsc(Long customerId);

    @EntityGraph(attributePaths = {"invoice", "customer"})
    @Query("""
            SELECT p
            FROM Payment p
            WHERE p.invoice.id IN :invoiceIds
            ORDER BY p.paymentDate DESC, p.id DESC
            """)
    List<Payment> findByInvoiceIds(@Param("invoiceIds") Collection<Long> invoiceIds);

    @Query("SELECT COALESCE(SUM(p.amountPaid), 0) FROM Payment p WHERE p.invoice.id = :invoiceId")
    BigDecimal sumAmountPaidByInvoiceId(@Param("invoiceId") Long invoiceId);

    @Query("SELECT COALESCE(SUM(p.amountPaid), 0) FROM Payment p")
    BigDecimal sumAllAmountPaid();

    long countByInvoiceId(Long invoiceId);

    @Query("""
            SELECT DISTINCT FUNCTION('YEAR', p.paymentDate)
            FROM Payment p
            WHERE p.customer.id = :customerId
            ORDER BY FUNCTION('YEAR', p.paymentDate) DESC
            """)
    List<Integer> findDistinctYearsByCustomerId(@Param("customerId") Long customerId);

    @Query("""
            SELECT DISTINCT FUNCTION('MONTH', p.paymentDate)
            FROM Payment p
            WHERE p.customer.id = :customerId
              AND FUNCTION('YEAR', p.paymentDate) = :year
            ORDER BY FUNCTION('MONTH', p.paymentDate) DESC
            """)
    List<Integer> findDistinctMonthsByCustomerIdAndYear(@Param("customerId") Long customerId, @Param("year") Integer year);
}
