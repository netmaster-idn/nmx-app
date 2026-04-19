package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.PaymentHistoryEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistoryEntry, Long> {

    @EntityGraph(attributePaths = {"customer", "invoice"})
    List<PaymentHistoryEntry> findByCustomerIdOrderByPaymentDateAscIdAsc(Long customerId);

    @EntityGraph(attributePaths = {"customer", "invoice"})
    List<PaymentHistoryEntry> findByCustomerIdAndPaymentDateBetweenOrderByPaymentDateAscIdAsc(Long customerId, LocalDate startDate, LocalDate endDate);

    @EntityGraph(attributePaths = {"customer", "invoice"})
    List<PaymentHistoryEntry> findByInvoiceIdOrderByPaymentDateAscIdAsc(Long invoiceId);

    @Modifying
    @Query("DELETE FROM PaymentHistoryEntry p WHERE p.invoice.id = :invoiceId")
    int deleteByInvoiceId(@Param("invoiceId") Long invoiceId);

    @Query("""
            SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
            FROM PaymentHistoryEntry p
            WHERE p.invoice.id = :invoiceId
              AND p.amount > 0
            """)
    boolean existsPositiveEntryByInvoiceId(@Param("invoiceId") Long invoiceId);

    @Query("""
            SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
            FROM PaymentHistoryEntry p
            WHERE p.invoice.id = :invoiceId
              AND p.description = :description
            """)
    boolean existsByInvoiceIdAndDescription(@Param("invoiceId") Long invoiceId, @Param("description") String description);

    @Query("""
            SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
            FROM PaymentHistoryEntry p
            WHERE p.customer.id = :customerId
              AND p.description = :description
              AND p.paymentDate BETWEEN :startDate AND :endDate
            """)
    boolean existsByCustomerIdAndDescriptionAndPaymentDateBetween(
            @Param("customerId") Long customerId,
            @Param("description") String description,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @EntityGraph(attributePaths = {"customer", "invoice"})
    List<PaymentHistoryEntry> findByCustomerIdAndInvoiceIdAndPaymentDate(Long customerId, Long invoiceId, LocalDate paymentDate);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM PaymentHistoryEntry p WHERE p.customer.id = :customerId")
    BigDecimal sumAmountByCustomerId(@Param("customerId") Long customerId);

    @Query("""
            SELECT MAX(p.paymentDate)
            FROM PaymentHistoryEntry p
            WHERE p.customer.id = :customerId
              AND p.amount > 0
            """)
    LocalDate findLastPaymentDateByCustomerId(@Param("customerId") Long customerId);
}
