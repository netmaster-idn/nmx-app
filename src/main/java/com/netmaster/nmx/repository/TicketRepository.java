package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    
    Optional<Ticket> findByTicketNumber(String ticketNumber);
    
    List<Ticket> findByStatus(String status);
    
    List<Ticket> findByPriority(String priority);
    
    List<Ticket> findByCategory(String category);
    
    List<Ticket> findByCustomerId(Long customerId);
    
    List<Ticket> findByAssignedTechnicianId(Long technicianId);

    long countByAssignedTechnicianId(Long technicianId);

    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.assignedTechnician.id = :technicianId AND t.status IN ('open', 'in_progress', 'pending')")
    long countActiveByAssignedTechnicianId(@Param("technicianId") Long technicianId);
    
    @Query("SELECT t FROM Ticket t WHERE t.status IN ('open', 'in_progress', 'pending') ORDER BY t.priority DESC, t.createdAt DESC")
    List<Ticket> findActiveTickets();
    
    @Query("SELECT COUNT(t) FROM Ticket t WHERE t.status = :status")
    Long countByStatus(String status);
}

