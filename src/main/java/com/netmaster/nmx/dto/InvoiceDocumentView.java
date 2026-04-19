package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDocumentView {

    private Long invoiceId;
    private String invoiceNumber;
    private String documentTitle;
    private String documentSubtitle;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private LocalDate serviceActiveUntil;
    private String issueDateLabel;
    private String dueDateLabel;
    private String serviceActiveUntilLabel;

    private String companyName;
    private String companyTagline;
    private String companyAddress;
    private String companyPhone;
    private String companyEmail;
    private String companyWebsite;
    private String companyLogoUrl;
    private String companyInitials;

    private String accountNumberLabel;
    private String paymentBankName;
    private String paymentMethodName;
    private String paymentAccountName;
    private String paymentAccountNumber;
    private String paymentAddress;
    private String paymentReferenceLabel;
    private String paymentReferenceNumber;
    private String paymentInstructions;

    private String customerName;
    private String customerCode;
    private String customerPhone;
    private String customerEmail;
    private String customerAddress;
    private LocalDate paymentDate;
    private String paymentDateLabel;
    private String paymentStatus;
    private String paymentStatusLabel;

    private List<InvoiceDocumentItemView> items;

    private BigDecimal subtotalAmount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String subtotalLabel;
    private String taxLabel;
    private String totalLabel;
    private String currencyCode;

    private String footerNote;
    private String footerEmail;
    private String footerAddress;

    private String qrPayload;
    private String qrCodeDataUri;
    private String qrCodeFormat;
    private boolean qrAvailable;
}
