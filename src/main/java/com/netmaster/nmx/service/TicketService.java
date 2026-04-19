package com.netmaster.nmx.service;

import com.netmaster.nmx.model.Ticket;

import java.util.List;

public interface TicketService {
    
    Ticket createTicket(Ticket ticket);
    
    Ticket updateTicket(Long id, Ticket ticket);
    
    void deleteTicket(Long id);
    
    Ticket getTicketById(Long id);
    
    Ticket getTicketByNumber(String ticketNumber);
    
    List<Ticket> getAllTickets();
    
    List<Ticket> getActiveTickets();
    
    List<Ticket> getTicketsByStatus(String status);
    
    List<Ticket> getTicketsByPriority(String priority);
    
    List<Ticket> getTicketsByCategory(String category);
    
    List<Ticket> getTicketsByCustomerId(Long customerId);
    
    List<Ticket> getTicketsByAssignedTechnician(Long technicianId);
    
    Ticket assignTechnician(Long ticketId, Long technicianId);
    
    Ticket updateStatus(Long ticketId, String status);
    
    Ticket resolveTicket(Long ticketId, String resolutionNotes);
    
    Long countByStatus(String status);
}

