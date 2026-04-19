package com.netmaster.nmx.service;

import com.netmaster.nmx.model.Ticket;
import com.netmaster.nmx.model.Technician;
import com.netmaster.nmx.model.User;
import com.netmaster.nmx.repository.TicketRepository;
import com.netmaster.nmx.repository.TechnicianRepository;
import com.netmaster.nmx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private final TicketRepository ticketRepository;
    private final TechnicianRepository technicianRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public Ticket createTicket(Ticket ticket) {
        ticket.setTicketNumber(generateTicketNumber());
        ticket.setStatus("open");
        return ticketRepository.save(ticket);
    }

    @Override
    @Transactional
    public Ticket updateTicket(Long id, Ticket ticket) {
        Ticket existing = ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket tidak ditemukan!"));
        
        existing.setSubject(ticket.getSubject());
        existing.setDescription(ticket.getDescription());
        existing.setPriority(ticket.getPriority());
        existing.setCategory(ticket.getCategory());
        
        return ticketRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteTicket(Long id) {
        ticketRepository.deleteById(id);
    }

    @Override
    public Ticket getTicketById(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ticket tidak ditemukan!"));
    }

    @Override
    public Ticket getTicketByNumber(String ticketNumber) {
        return ticketRepository.findByTicketNumber(ticketNumber)
                .orElseThrow(() -> new RuntimeException("Ticket tidak ditemukan!"));
    }

    @Override
    public List<Ticket> getAllTickets() {
        return ticketRepository.findAll();
    }

    @Override
    public List<Ticket> getActiveTickets() {
        return ticketRepository.findActiveTickets();
    }

    @Override
    public List<Ticket> getTicketsByStatus(String status) {
        return ticketRepository.findByStatus(status);
    }

    @Override
    public List<Ticket> getTicketsByPriority(String priority) {
        return ticketRepository.findByPriority(priority);
    }

    @Override
    public List<Ticket> getTicketsByCategory(String category) {
        return ticketRepository.findByCategory(category);
    }

    @Override
    public List<Ticket> getTicketsByCustomerId(Long customerId) {
        return ticketRepository.findByCustomerId(customerId);
    }

    @Override
    public List<Ticket> getTicketsByAssignedTechnician(Long technicianId) {
        return ticketRepository.findByAssignedTechnicianId(technicianId);
    }

    @Override
    @Transactional
    public Ticket assignTechnician(Long ticketId, Long technicianId) {
        Ticket ticket = getTicketById(ticketId);
        Technician technician = technicianRepository.findById(technicianId)
                .orElseThrow(() -> new RuntimeException("Teknisi tidak ditemukan!"));
        
        ticket.setAssignedTechnician(technician);
        ticket.setStatus("in_progress");
        
        return ticketRepository.save(ticket);
    }

    @Override
    @Transactional
    public Ticket updateStatus(Long ticketId, String status) {
        Ticket ticket = getTicketById(ticketId);
        ticket.setStatus(status);
        
        if ("resolved".equals(status)) {
            ticket.setResolvedAt(LocalDateTime.now());
        } else if ("closed".equals(status)) {
            ticket.setClosedAt(LocalDateTime.now());
        }
        
        return ticketRepository.save(ticket);
    }

    @Override
    @Transactional
    public Ticket resolveTicket(Long ticketId, String resolutionNotes) {
        Ticket ticket = getTicketById(ticketId);
        ticket.setStatus("resolved");
        ticket.setResolvedAt(LocalDateTime.now());
        ticket.setResolutionNotes(resolutionNotes);
        
        return ticketRepository.save(ticket);
    }

    @Override
    public Long countByStatus(String status) {
        return ticketRepository.countByStatus(status);
    }

    private String generateTicketNumber() {
        String prefix = "TKT";
        String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String searchPrefix = prefix + "-" + today;
        
        Integer maxSeq = ticketRepository.findAll().stream()
                .filter(t -> t.getTicketNumber() != null && t.getTicketNumber().startsWith(searchPrefix))
                .map(t -> {
                    try {
                        return Integer.parseInt(t.getTicketNumber().replace(searchPrefix + "-", ""));
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .max(Integer::compareTo)
                .orElse(0);
        
        int nextSeq = maxSeq + 1;
        return prefix + "-" + today + "-" + String.format("%04d", nextSeq);
    }
}

