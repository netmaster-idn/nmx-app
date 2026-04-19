package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.WhatsappReminderDispatchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface WhatsappReminderDispatchLogRepository extends JpaRepository<WhatsappReminderDispatchLog, Long> {

    boolean existsByInvoiceIdAndDispatchStatusIgnoreCase(Long invoiceId, String dispatchStatus);

    @Query("""
            SELECT COUNT(l)
            FROM WhatsappReminderDispatchLog l
            WHERE LOWER(l.dispatchStatus) = 'sent'
              AND l.sentAt >= :start
              AND l.sentAt < :end
            """)
    long countSentWithinWindow(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            SELECT l
            FROM WhatsappReminderDispatchLog l
            JOIN FETCH l.invoice i
            JOIN FETCH i.customer c
            ORDER BY COALESCE(l.sentAt, l.createdAt) DESC, l.id DESC
            """)
    List<WhatsappReminderDispatchLog> findRecentHistory();
}

