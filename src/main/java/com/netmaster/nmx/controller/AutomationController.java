package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.model.AutomationRule;
import com.netmaster.nmx.repository.AutomationRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/automation")
@RequiredArgsConstructor
public class AutomationController {

    private final AutomationRuleRepository ruleRepository;

    // Get all automation rules
    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<List<AutomationRule>>> getAllRules() {
        List<AutomationRule> rules = ruleRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success("Data rule berhasil diambil", rules));
    }

    // Get rule by ID
    @GetMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<AutomationRule>> getRule(@PathVariable Long id) {
        return ruleRepository.findById(id)
                .map(rule -> ResponseEntity.ok(ApiResponse.success("Rule ditemukan", rule)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Rule tidak ditemukan")));
    }

    // Create new rule
    @PostMapping("/rules")
    public ResponseEntity<ApiResponse<AutomationRule>> createRule(@RequestBody AutomationRule rule) {
        AutomationRule saved = ruleRepository.save(rule);
        return ResponseEntity.ok(ApiResponse.success("Rule berhasil dibuat", saved));
    }

    // Update rule
    @PutMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<AutomationRule>> updateRule(@PathVariable Long id, @RequestBody AutomationRule rule) {
        if (!ruleRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Rule tidak ditemukan"));
        }
        rule.setId(id);
        AutomationRule updated = ruleRepository.save(rule);
        return ResponseEntity.ok(ApiResponse.success("Rule berhasil diperbarui", updated));
    }

    // Delete rule
    @DeleteMapping("/rules/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable Long id) {
        if (!ruleRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Rule tidak ditemukan"));
        }
        ruleRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Rule berhasil dihapus", null));
    }

    // Toggle rule active status
    @PostMapping("/rules/{id}/toggle")
    public ResponseEntity<ApiResponse<AutomationRule>> toggleRule(@PathVariable Long id) {
        return ruleRepository.findById(id)
                .map(rule -> {
                    rule.setActive(!rule.isActive());
                    AutomationRule updated = ruleRepository.save(rule);
                    return ResponseEntity.ok(ApiResponse.success("Rule status diubah", updated));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Rule tidak ditemukan")));
    }

    // Get automation statistics
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Object>> getStats() {
        List<AutomationRule> all = ruleRepository.findAll();
        long total = all.size();
        long active = all.stream().filter(r -> r.isActive()).count();
        long totalExecutions = all.stream()
                .mapToInt(r -> r.getExecutionCount() != null ? r.getExecutionCount() : 0)
                .sum();

        return ResponseEntity.ok(ApiResponse.success("Statistik automation", java.util.Map.of(
                "total", total,
                "active", active,
                "totalExecutions", totalExecutions
        )));
    }

    // Get active rules
    @GetMapping("/rules/active")
    public ResponseEntity<ApiResponse<List<AutomationRule>>> getActiveRules() {
        List<AutomationRule> rules = ruleRepository.findAll().stream()
                .filter(r -> r.isActive())
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Data rule aktif", rules));
    }
}

