package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.InvoiceDocumentView;
import com.netmaster.nmx.service.InvoiceDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class InvoiceDocumentController {

    private final InvoiceDocumentService invoiceDocumentService;

    @GetMapping("/finance/invoice/{id}/document")
    public String viewInvoiceDocument(@PathVariable Long id, Model model) {
        if (!hasReadPermission()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Akses ditolak");
        }

        InvoiceDocumentView document = invoiceDocumentService.getInvoiceDocument(id);
        model.addAttribute("document", document);
        return "finance/invoice-document";
    }

    @GetMapping("/api/invoices/{id}/document")
    @ResponseBody
    public ResponseEntity<ApiResponse<InvoiceDocumentView>> getInvoiceDocument(@PathVariable Long id) {
        if (!hasReadPermission()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }

        try {
            return ResponseEntity.ok(ApiResponse.success("Dokumen invoice berhasil diambil", invoiceDocumentService.getInvoiceDocument(id)));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/finance/invoice/{id}/document/pdf")
    public ResponseEntity<byte[]> downloadInvoiceDocumentPdf(@PathVariable Long id,
                                                             @RequestParam(name = "paperSize", defaultValue = "80") String paperSize) {
        if (!hasReadPermission()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Akses ditolak");
        }

        InvoiceDocumentView document = invoiceDocumentService.getInvoiceDocument(id);
        byte[] pdfBytes = invoiceDocumentService.renderInvoiceDocumentPdf(id, paperSize);
        String normalizedPaperSize = "58".equals(paperSize) ? "58" : "80";
        String fileName = buildPdfFileName(document, normalizedPaperSize);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    private boolean hasReadPermission() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (authority.getAuthority() != null && authority.getAuthority().startsWith("ROLE_")) {
                return true;
            }
        }
        return false;
    }

    private String buildPdfFileName(InvoiceDocumentView document, String paperSize) {
        String invoiceNumber = sanitizeFileNamePart(document.getInvoiceNumber(), "STRUK-PEMBAYARAN");
        LocalDate paymentMonthDate = document.getPaymentDate() != null
                ? document.getPaymentDate()
                : document.getDueDate() != null ? document.getDueDate() : document.getIssueDate();
        String paymentMonth = paymentMonthDate != null
                ? paymentMonthDate.getMonth().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("id-ID")).toUpperCase(Locale.ROOT)
                : "PEMBAYARAN";
        String customerName = sanitizeFileNamePart(document.getCustomerName(), "PELANGGAN");
        return invoiceNumber + "-" + sanitizeFileNamePart(paymentMonth, "PEMBAYARAN") + "-" + customerName + "-" + paperSize + "mm.pdf";
    }

    private String sanitizeFileNamePart(String value, String fallback) {
        String baseValue = value != null && !value.isBlank() ? value : fallback;
        String normalized = Normalizer.normalize(baseValue, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\p{Alnum}]+", "-")
                .replaceAll("(^-+|-+$)", "")
                .replaceAll("-{2,}", "-")
                .toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? fallback : normalized;
    }
}
