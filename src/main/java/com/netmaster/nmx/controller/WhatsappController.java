package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.WhatsappBotHistoryItemData;
import com.netmaster.nmx.dto.WhatsappGatewayBootstrapStatusData;
import com.netmaster.nmx.dto.WhatsappReminderSettingsData;
import com.netmaster.nmx.dto.WhatsappReminderSettingsRequest;
import com.netmaster.nmx.dto.WhatsappSendDocumentRequest;
import com.netmaster.nmx.dto.WhatsappSendMessageRequest;
import com.netmaster.nmx.dto.WhatsappStatusData;
import com.netmaster.nmx.security.TenantRoleAccess;
import com.netmaster.nmx.service.WhatsappGatewayBootstrapService;
import com.netmaster.nmx.service.WhatsappGatewayService;
import com.netmaster.nmx.service.WhatsappReminderAutomationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/whatsapp")
@RequiredArgsConstructor
@Slf4j
public class WhatsappController {

    private final WhatsappGatewayBootstrapService whatsappGatewayBootstrapService;
    private final WhatsappGatewayService whatsappGatewayService;
    private final WhatsappReminderAutomationService whatsappReminderAutomationService;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<WhatsappStatusData>> getStatus() {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        return ResponseEntity.ok(whatsappGatewayService.getStatus());
    }

    @PostMapping("/init")
    public ResponseEntity<ApiResponse<WhatsappStatusData>> init() {
        return handleWriteAction(whatsappGatewayService::initClient);
    }

    @GetMapping("/qr")
    public ResponseEntity<ApiResponse<WhatsappStatusData>> getQr() {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        return ResponseEntity.ok(whatsappGatewayService.getQrCode());
    }

    @PostMapping("/qr/regenerate")
    public ResponseEntity<ApiResponse<WhatsappStatusData>> regenerateQr() {
        return handleWriteAction(whatsappGatewayService::regenerateQrCode);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<WhatsappStatusData>> logout() {
        return handleWriteAction(whatsappGatewayService::logout);
    }

    @PostMapping("/reset-session")
    public ResponseEntity<ApiResponse<WhatsappStatusData>> resetSession() {
        return handleWriteAction(whatsappGatewayService::resetSession);
    }

    @GetMapping("/gateway/bootstrap-status")
    public ResponseEntity<ApiResponse<WhatsappGatewayBootstrapStatusData>> getBootstrapStatus() {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Status instalasi WhatsApp gateway berhasil diambil",
                whatsappGatewayBootstrapService.getStatus()
        ));
    }

    @PostMapping("/gateway/install")
    public ResponseEntity<ApiResponse<WhatsappGatewayBootstrapStatusData>> installGateway() {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        return ResponseEntity.accepted().body(ApiResponse.success(
                "Instalasi WhatsApp gateway dijalankan di belakang layar",
                whatsappGatewayBootstrapService.installAndStartAsync()
        ));
    }

    @GetMapping("/chats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getChats(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String search) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        try {
            return ResponseEntity.ok(whatsappGatewayService.getChatOverview(limit, search));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/chats/{chatId}/messages")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getChatMessages(
            @PathVariable String chatId,
            @RequestParam(required = false) Integer limit) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        try {
            return ResponseEntity.ok(whatsappGatewayService.getChatMessages(chatId, limit));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/messages/{messageId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMessageStatus(@PathVariable String messageId) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        try {
            return ResponseEntity.ok(whatsappGatewayService.getMessageStatus(messageId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/messages/send")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendMessage(@RequestBody WhatsappSendMessageRequest request) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        try {
            return ResponseEntity.ok(whatsappGatewayService.sendMessage(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping("/messages/send-document")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendDocument(@RequestBody WhatsappSendDocumentRequest request) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        try {
            return ResponseEntity.ok(whatsappGatewayService.sendDocument(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<WhatsappBotHistoryItemData>>> getHistory() {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Riwayat BOT WhatsApp berhasil diambil",
                    whatsappReminderAutomationService.getBotHistory()
            ));
        } catch (Exception ex) {
            log.warn("Failed to load WhatsApp BOT history: {}", resolveErrorMessage(ex), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Riwayat BOT WhatsApp belum tersedia. Silakan sinkronkan schema database terlebih dahulu."));
        }
    }

    @GetMapping("/schedule-overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getScheduleOverview() {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Ringkasan jadwal WhatsApp berhasil diambil",
                whatsappReminderAutomationService.getScheduleOverview()
        ));
    }

    @GetMapping("/reminder-settings")
    public ResponseEntity<ApiResponse<WhatsappReminderSettingsData>> getReminderSettings() {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Setting reminder WhatsApp berhasil diambil",
                    whatsappReminderAutomationService.getSettings()
            ));
        } catch (Exception ex) {
            String error = resolveErrorMessage(ex);
            log.warn("Failed to load WhatsApp reminder settings: {}", error, ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
        }
    }

    @PostMapping("/reminder-settings")
    public ResponseEntity<ApiResponse<WhatsappReminderSettingsData>> updateReminderSettings(@Valid @RequestBody WhatsappReminderSettingsRequest request) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Setting reminder WhatsApp berhasil diperbarui",
                    whatsappReminderAutomationService.updateSettings(request)
            ));
        } catch (Exception ex) {
            String error = resolveErrorMessage(ex);
            log.warn("Failed to update WhatsApp reminder settings: {}", error, ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
        }
    }

    @GetMapping("/invoices/{invoiceId}/manual-draft")
    public ResponseEntity<ApiResponse<Map<String, String>>> getManualInvoiceDraft(@PathVariable Long invoiceId) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }

        try {
            Map<String, String> payload = whatsappReminderAutomationService.buildManualInvoiceDraft(invoiceId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Draft invoice/tagihan WhatsApp berhasil disiapkan",
                    payload
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }
    @PostMapping("/invoices/{invoiceId}/manual-send")
    public ResponseEntity<ApiResponse<Map<String, String>>> sendManualInvoiceOrReceipt(@PathVariable Long invoiceId) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }

        try {
            Map<String, String> payload = whatsappReminderAutomationService.sendManualInvoiceOrReceipt(invoiceId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Kirim manual " + payload.getOrDefault("documentLabel", "dokumen") + " berhasil",
                    payload
            ));
        } catch (UnexpectedRollbackException ex) {
            return resolveManualSendAfterRollbackOnly(invoiceId, ex);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error(ex.getMessage()));
        } catch (Exception ex) {
            String error = resolveErrorMessage(ex);
            log.warn("Failed to send manual WhatsApp invoice/receipt for invoice {}: {}", invoiceId, error, ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(error));
        }
    }

    private ResponseEntity<ApiResponse<Map<String, String>>> resolveManualSendAfterRollbackOnly(Long invoiceId, UnexpectedRollbackException ex) {
        try {
            Map<String, String> payload = whatsappReminderAutomationService.resolveManualSendResultAfterRollback(invoiceId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Kirim manual " + payload.getOrDefault("documentLabel", "dokumen") + " berhasil",
                    payload
            ));
        } catch (Exception ignored) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(ex.getMessage()));
        }
    }

    private String resolveErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "Request gagal diproses.";
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String top = throwable.getMessage();
        String bottom = root.getMessage();
        if (bottom != null && !bottom.isBlank()) {
            if (top == null || top.isBlank() || top.equalsIgnoreCase(bottom)) {
                return bottom;
            }
            return top + " | root cause: " + bottom;
        }
        return (top == null || top.isBlank()) ? "Request gagal diproses." : top;
    }

    private ResponseEntity<ApiResponse<WhatsappStatusData>> handleWriteAction(ActionSupplier supplier) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }

        try {
            return ResponseEntity.ok(supplier.get());
        } catch (IllegalStateException ex) {
            return ResponseEntity.ok(ApiResponse.success(ex.getMessage(), offlineStatus(ex.getMessage())));
        }
    }

    private WhatsappStatusData offlineStatus(String message) {
        return new WhatsappStatusData(
                "error",
                "Error",
                false,
                false,
                null,
                "default",
                null,
                null,
                message,
                null
        );
    }

    private boolean hasPermission(String permission) {
        return switch (permission) {
            case "READ" -> TenantRoleAccess.canRead(SecurityContextHolder.getContext().getAuthentication());
            case "UPDATE" -> TenantRoleAccess.canWrite(SecurityContextHolder.getContext().getAuthentication());
            default -> false;
        };
    }

    @FunctionalInterface
    private interface ActionSupplier {
        ApiResponse<WhatsappStatusData> get();
    }
}



