package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.TechnicianRequest;
import com.netmaster.nmx.dto.TechnicianView;
import com.netmaster.nmx.model.Ticket;
import com.netmaster.nmx.model.Technician;
import com.netmaster.nmx.service.TicketService;
import com.netmaster.nmx.service.ICustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/ticketing")
@RequiredArgsConstructor
public class TicketingController {

    private final TicketService ticketService;
    private final ICustomerService customerService;

    // ==================== THYMELEAF PAGES ====================

    @GetMapping("/baru")
    public String baruPage(Model model) {
        model.addAttribute("customers", customerService.getAllCustomers());
        model.addAttribute("technicians", customerService.getAllTechnicians());
        model.addAttribute("page", "tiket-baru");
        return "layout/base";
    }

    @GetMapping("/aktif")
    public String aktifPage(Model model) {
        model.addAttribute("page", "tiket-aktif");
        return "layout/base";
    }

    @GetMapping("/sla")
    public String slaPage(Model model) {
        model.addAttribute("page", "sla");
        return "layout/base";
    }

    @GetMapping("/teknisi")
    public String teknisiPage(Model model) {
        model.addAttribute("technicians", customerService.getAllTechnicians());
        model.addAttribute("page", "teknisi");
        return "layout/base";
    }

    // ==================== REST API ====================

    @GetMapping("/api/tickets")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Ticket>>> getAllTickets() {
        List<Ticket> tickets = ticketService.getAllTickets();
        return ResponseEntity.ok(ApiResponse.success("Data ticket berhasil diambil", tickets));
    }

    @GetMapping("/api/technicians")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<TechnicianView>>> getAllTechnicians() {
        return ResponseEntity.ok(ApiResponse.success(
                "Data teknisi berhasil diambil",
                customerService.getAllTechnicianViews()
        ));
    }

    @PostMapping("/api/technicians")
    @ResponseBody
    public ResponseEntity<ApiResponse<TechnicianView>> createTechnician(@RequestBody TechnicianRequest request) {
        try {
            TechnicianView technician = customerService.createTechnician(request);
            return ResponseEntity.ok(ApiResponse.success("Teknisi berhasil ditambahkan!", technician));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/api/technicians/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<TechnicianView>> updateTechnician(
            @PathVariable Long id,
            @RequestBody TechnicianRequest request
    ) {
        try {
            TechnicianView technician = customerService.updateTechnician(id, request);
            return ResponseEntity.ok(ApiResponse.success("Teknisi berhasil diperbarui!", technician));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/api/technicians/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> deleteTechnician(@PathVariable Long id) {
        try {
            customerService.deleteTechnician(id);
            return ResponseEntity.ok(ApiResponse.success("Teknisi berhasil dihapus!", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/tickets/active")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Ticket>>> getActiveTickets() {
        List<Ticket> tickets = ticketService.getActiveTickets();
        return ResponseEntity.ok(ApiResponse.success("Data ticket aktif berhasil diambil", tickets));
    }

    @GetMapping("/api/tickets/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Ticket>> getTicket(@PathVariable Long id) {
        try {
            Ticket ticket = ticketService.getTicketById(id);
            return ResponseEntity.ok(ApiResponse.success("Data ticket ditemukan", ticket));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/tickets/number/{ticketNumber}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Ticket>> getTicketByNumber(@PathVariable String ticketNumber) {
        try {
            Ticket ticket = ticketService.getTicketByNumber(ticketNumber);
            return ResponseEntity.ok(ApiResponse.success("Data ticket ditemukan", ticket));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/api/tickets")
    @ResponseBody
    public ResponseEntity<ApiResponse<Ticket>> createTicket(@RequestBody Ticket ticket) {
        try {
            Ticket created = ticketService.createTicket(ticket);
            return ResponseEntity.ok(ApiResponse.success("Ticket berhasil dibuat!", created));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/api/tickets/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Ticket>> updateTicket(@PathVariable Long id, @RequestBody Ticket ticket) {
        try {
            Ticket updated = ticketService.updateTicket(id, ticket);
            return ResponseEntity.ok(ApiResponse.success("Ticket berhasil diperbarui!", updated));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/api/tickets/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> deleteTicket(@PathVariable Long id) {
        try {
            ticketService.deleteTicket(id);
            return ResponseEntity.ok(ApiResponse.success("Ticket berhasil dihapus!", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/api/tickets/{id}/assign")
    @ResponseBody
    public ResponseEntity<ApiResponse<Ticket>> assignTechnician(
            @PathVariable Long id, 
            @RequestParam Long technicianId) {
        try {
            Ticket ticket = ticketService.assignTechnician(id, technicianId);
            return ResponseEntity.ok(ApiResponse.success("Teknisi berhasil ditugaskan!", ticket));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/api/tickets/{id}/status")
    @ResponseBody
    public ResponseEntity<ApiResponse<Ticket>> updateStatus(
            @PathVariable Long id, 
            @RequestParam String status) {
        try {
            Ticket ticket = ticketService.updateStatus(id, status);
            return ResponseEntity.ok(ApiResponse.success("Status ticket berhasil diperbarui!", ticket));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/api/tickets/{id}/resolve")
    @ResponseBody
    public ResponseEntity<ApiResponse<Ticket>> resolveTicket(
            @PathVariable Long id, 
            @RequestParam(required = false) String resolutionNotes) {
        try {
            Ticket ticket = ticketService.resolveTicket(id, resolutionNotes);
            return ResponseEntity.ok(ApiResponse.success("Ticket berhasil diselesaikan!", ticket));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/tickets/status/{status}")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Ticket>>> getTicketsByStatus(@PathVariable String status) {
        List<Ticket> tickets = ticketService.getTicketsByStatus(status);
        return ResponseEntity.ok(ApiResponse.success("Data ticket berhasil diambil", tickets));
    }

    @GetMapping("/api/tickets/priority/{priority}")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Ticket>>> getTicketsByPriority(@PathVariable String priority) {
        List<Ticket> tickets = ticketService.getTicketsByPriority(priority);
        return ResponseEntity.ok(ApiResponse.success("Data ticket berhasil diambil", tickets));
    }

    @GetMapping("/api/tickets/category/{category}")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Ticket>>> getTicketsByCategory(@PathVariable String category) {
        List<Ticket> tickets = ticketService.getTicketsByCategory(category);
        return ResponseEntity.ok(ApiResponse.success("Data ticket berhasil diambil", tickets));
    }

    @GetMapping("/api/tickets/customer/{customerId}")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Ticket>>> getTicketsByCustomer(@PathVariable Long customerId) {
        List<Ticket> tickets = ticketService.getTicketsByCustomerId(customerId);
        return ResponseEntity.ok(ApiResponse.success("Data ticket berhasil diambil", tickets));
    }

    @GetMapping("/api/tickets/technician/{technicianId}")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Ticket>>> getTicketsByTechnician(@PathVariable Long technicianId) {
        List<Ticket> tickets = ticketService.getTicketsByAssignedTechnician(technicianId);
        return ResponseEntity.ok(ApiResponse.success("Data ticket berhasil diambil", tickets));
    }

    @GetMapping("/api/stats")
    @ResponseBody
    public ResponseEntity<ApiResponse<Object>> getStats() {
        Long open = ticketService.countByStatus("open");
        Long inProgress = ticketService.countByStatus("in_progress");
        Long resolved = ticketService.countByStatus("resolved");
        Long closed = ticketService.countByStatus("closed");

        return ResponseEntity.ok(ApiResponse.success("Statistik ticket", java.util.Map.of(
                "open", open,
                "in_progress", inProgress,
                "resolved", resolved,
                "closed", closed,
                "total", open + inProgress + resolved + closed
        )));
    }
}

