package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.InvoiceDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InvoiceDeliveryLogRepository extends JpaRepository<InvoiceDeliveryLog, Long> {

    List<InvoiceDeliveryLog> findTop200ByOrderByCreatedAtDesc();

    List<InvoiceDeliveryLog> findByInvoiceIdInOrderByCreatedAtDesc(Collection<Long> invoiceIds);

    Optional<InvoiceDeliveryLog> findTopByInvoiceIdAndMessageTypeOrderByCreatedAtDesc(Long invoiceId, String messageType);

    boolean existsByInvoiceIdAndMessageTypeAndStatusIgnoreCase(Long invoiceId, String messageType, String status);

    @Query("""
            SELECT COUNT(l)
            FROM InvoiceDeliveryLog l
            WHERE LOWER(l.status) = LOWER(:status)
              AND l.createdAt >= :start
              AND l.createdAt < :end
            """)
    long countByStatusWithin(@Param("status") String status,
                             @Param("start") LocalDateTime start,
                             @Param("end") LocalDateTime end);
}
