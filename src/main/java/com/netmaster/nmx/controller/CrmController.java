package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.model.CrmContact;
import com.netmaster.nmx.repository.CrmContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/crm")
@RequiredArgsConstructor
public class CrmController {

    private final CrmContactRepository crmRepository;

    // Get all CRM contacts
    @GetMapping("/contacts")
    public ResponseEntity<ApiResponse<List<CrmContact>>> getAllContacts() {
        List<CrmContact> contacts = crmRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success("Data kontak berhasil diambil", contacts));
    }

    // Get contact by ID
    @GetMapping("/contacts/{id}")
    public ResponseEntity<ApiResponse<CrmContact>> getContact(@PathVariable Long id) {
        return crmRepository.findById(id)
                .map(contact -> ResponseEntity.ok(ApiResponse.success("Kontak ditemukan", contact)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Kontak tidak ditemukan")));
    }

    // Create new contact
    @PostMapping("/contacts")
    public ResponseEntity<ApiResponse<CrmContact>> createContact(@RequestBody CrmContact contact) {
        CrmContact saved = crmRepository.save(contact);
        return ResponseEntity.ok(ApiResponse.success("Kontak berhasil dibuat", saved));
    }

    // Update contact
    @PutMapping("/contacts/{id}")
    public ResponseEntity<ApiResponse<CrmContact>> updateContact(@PathVariable Long id, @RequestBody CrmContact contact) {
        if (!crmRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Kontak tidak ditemukan"));
        }
        contact.setId(id);
        CrmContact updated = crmRepository.save(contact);
        return ResponseEntity.ok(ApiResponse.success("Kontak berhasil diperbarui", updated));
    }

    // Delete contact
    @DeleteMapping("/contacts/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteContact(@PathVariable Long id) {
        if (!crmRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Kontak tidak ditemukan"));
        }
        crmRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Kontak berhasil dihapus", null));
    }

    // Get CRM statistics
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getStats() {
        List<CrmContact> all = crmRepository.findAll();
        long total = all.size();
        long leads = all.stream().filter(c -> "lead".equals(c.getStatus())).count();
        long prospects = all.stream().filter(c -> "prospect".equals(c.getStatus())).count();
        long customers = all.stream().filter(c -> "customer".equals(c.getStatus())).count();

        return ResponseEntity.ok(ApiResponse.success("Statistik CRM", java.util.Map.of(
                "total", total,
                "leads", leads,
                "prospects", prospects,
                "customers", customers
        )));
    }

    // Get contacts by status
    @GetMapping("/contacts/status/{status}")
    public ResponseEntity<ApiResponse<List<CrmContact>>> getContactsByStatus(@PathVariable String status) {
        List<CrmContact> contacts = crmRepository.findAll().stream()
                .filter(c -> status.equalsIgnoreCase(c.getStatus()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Data kontak berhasil diambil", contacts));
    }

    // Search contacts
    @GetMapping("/contacts/search")
    public ResponseEntity<ApiResponse<List<CrmContact>>> searchContacts(@RequestParam String keyword) {
        List<CrmContact> contacts = crmRepository.findAll().stream()
                .filter(c -> c.getName().toLowerCase().contains(keyword.toLowerCase()) ||
                        (c.getEmail() != null && c.getEmail().toLowerCase().contains(keyword.toLowerCase())) ||
                        (c.getPhone() != null && c.getPhone().contains(keyword)) ||
                        (c.getCompany() != null && c.getCompany().toLowerCase().contains(keyword.toLowerCase())))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Hasil pencarian", contacts));
    }
}

