package com.netmaster.nmx.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.netmaster.nmx.dto.InvoiceDocumentItemView;
import com.netmaster.nmx.dto.InvoiceDocumentView;
import com.netmaster.nmx.model.BankAccount;
import com.netmaster.nmx.model.CompanyProfile;
import com.netmaster.nmx.model.CompanySetting;
import com.netmaster.nmx.model.Customer;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.model.Invoice;
import com.netmaster.nmx.model.InvoiceItem;
import com.netmaster.nmx.model.PaymentMethod;
import com.netmaster.nmx.repository.BankAccountRepository;
import com.netmaster.nmx.repository.CompanyProfileRepository;
import com.netmaster.nmx.repository.CompanySettingRepository;
import com.netmaster.nmx.repository.InvoiceItemRepository;
import com.netmaster.nmx.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.awt.Color;

@Service
@RequiredArgsConstructor
public class InvoiceDocumentService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final CompanySettingRepository companySettingRepository;
    private final BankAccountRepository bankAccountRepository;
    private final InvoiceDocumentSupport support;
    private final JdbcTemplate jdbcTemplate;

    public InvoiceDocumentView getInvoiceDocument(Long invoiceId) {
        Invoice invoice = invoiceRepository.findDocumentById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice tidak ditemukan!"));

        CompanyProfile company = resolveCompany(invoice);
        CompanySetting settings = resolveCompanySetting(company);
        BankAccount bankAccount = resolveBankAccount(invoice, company);
        persistResolvedInvoiceContext(invoice, company, bankAccount);
        PaymentMethod paymentMethod = resolvePaymentMethod(invoice, bankAccount);
        Customer customer = invoice.getCustomer();
        CustomerServiceEntity service = invoice.getCustomerService();

        String localeCode = settings != null ? settings.getDefaultLocaleCode() : "id-ID";
        String currencyCode = support.hasText(invoice.getCurrencyCode())
                ? invoice.getCurrencyCode()
                : settings != null ? settings.getDefaultCurrencyCode() : "IDR";

        List<InvoiceDocumentItemView> items = buildItems(invoice, currencyCode, localeCode);
        BigDecimal subtotal = resolveSubtotal(invoice, items);
        BigDecimal taxRate = resolveTaxRate(invoice, settings);
        BigDecimal taxAmount = resolveTaxAmount(invoice, subtotal, taxRate);
        BigDecimal totalAmount = resolveTotalAmount(invoice, subtotal, taxAmount);
        String qrPayload = "";
        String qrCodeDataUri = null;

        LocalDate issueDate = support.safeDate(
                invoice.getIssueDate(),
                invoice.getCreatedAt() != null ? invoice.getCreatedAt().toLocalDate() : invoice.getBillingMonth()
        );
        LocalDate dueDate = support.safeDate(invoice.getDueDate(), invoice.getBillingMonth());
        LocalDate paymentDate = support.safeDate(invoice.getPaymentDate(), null);
        LocalDate serviceActiveUntil = resolveServiceActiveUntil(invoice, dueDate, paymentDate);

        String companyAddress = buildCompanyAddress(company);
        String paymentAddress = buildPaymentAddress(bankAccount);
        String paymentReferenceLabel = resolvePaymentReferenceLabel(bankAccount);
        String paymentReferenceNumber = support.safeLine(invoice.getReferenceNumber(), invoice.getInvoiceNumber());

        return InvoiceDocumentView.builder()
                .invoiceId(invoice.getId())
                .invoiceNumber(support.safeValue(invoice.getInvoiceNumber()))
                .documentTitle(resolveDocumentTitle(settings))
                .documentSubtitle(resolveDocumentSubtitle(invoice, settings))
                .issueDate(issueDate)
                .dueDate(dueDate)
                .serviceActiveUntil(serviceActiveUntil)
                .issueDateLabel(support.formatDate(issueDate, localeCode))
                .dueDateLabel(support.formatDate(dueDate, localeCode))
                .serviceActiveUntilLabel(support.formatDate(serviceActiveUntil, localeCode))
                .paymentDate(paymentDate)
                .paymentDateLabel(paymentDate != null ? support.formatDate(paymentDate, localeCode) : InvoiceDocumentSupport.DEFAULT_TEXT)
                .paymentStatus(support.safeValue(invoice.getStatus()))
                .paymentStatusLabel(resolvePaymentStatusLabel(invoice))
                .companyName(support.safeValue(company != null ? company.getName() : null))
                .companyTagline(support.safeValue(company != null ? company.getTagline() : null))
                .companyAddress(companyAddress)
                .companyPhone(support.safeValue(company != null ? company.getPhone() : null))
                .companyEmail(support.safeLine(company != null ? company.getSupportEmail() : null, company != null ? company.getEmail() : null))
                .companyWebsite(support.safeValue(company != null ? company.getWebsite() : null))
                .companyLogoUrl(company != null ? company.getLogoUrl() : null)
                .companyInitials(support.initials(company != null ? company.getName() : null))
                .accountNumberLabel(support.safeValue(bankAccount != null ? bankAccount.getAccountNumber() : null))
                .paymentBankName(support.safeValue(bankAccount != null ? bankAccount.getBankName() : null))
                .paymentMethodName(resolvePaymentMethodName(paymentMethod, invoice))
                .paymentAccountName(support.safeValue(bankAccount != null ? bankAccount.getAccountName() : null))
                .paymentAccountNumber(support.safeValue(bankAccount != null ? bankAccount.getAccountNumber() : null))
                .paymentAddress(paymentAddress)
                .paymentReferenceLabel(paymentReferenceLabel)
                .paymentReferenceNumber(paymentReferenceNumber)
                .paymentInstructions(support.safeLine(
                        bankAccount != null ? bankAccount.getInstructions() : null,
                        paymentMethod != null ? paymentMethod.getInstructions() : null,
                        invoice.getPaymentNotes()
                ))
                .customerName(support.safeValue(customer != null ? customer.getFullName() : null))
                .customerCode(support.safeValue(customer != null ? customer.getCustomerCode() : null))
                .customerPhone(support.safeValue(customer != null ? customer.getPhone() : null))
                .customerEmail(support.safeValue(customer != null ? customer.getEmail() : null))
                .customerAddress(buildCustomerAddress(customer, service))
                .items(items)
                .subtotalAmount(subtotal)
                .taxRate(taxRate)
                .taxAmount(taxAmount)
                .totalAmount(totalAmount)
                .subtotalLabel(support.formatMoney(subtotal, currencyCode, localeCode))
                .taxLabel(support.formatMoney(taxAmount, currencyCode, localeCode))
                .totalLabel(support.formatMoney(totalAmount, currencyCode, localeCode))
                .currencyCode(currencyCode)
                .footerNote(resolveFooterNote(invoice, settings))
                .footerEmail(support.safeLine(company != null ? company.getSupportEmail() : null, company != null ? company.getEmail() : null))
                .footerAddress(companyAddress)
                .qrPayload(support.safeValue(qrPayload))
                .qrCodeDataUri(qrCodeDataUri)
                .qrCodeFormat("-")
                .qrAvailable(false)
                .build();
    }

    public byte[] renderInvoiceDocumentPdf(Long invoiceId, String paperSize) {
        InvoiceDocumentView view = getInvoiceDocument(invoiceId);
        String normalizedPaperSize = "58".equals(paperSize) ? "58" : "80";
        Rectangle pageSize = buildThermalPageSize(normalizedPaperSize, view);
        Font titleFont = FontFactory.getFont(FontFactory.COURIER_BOLD, "58".equals(normalizedPaperSize) ? 11 : 13);
        Font bodyFont = FontFactory.getFont(FontFactory.COURIER, "58".equals(normalizedPaperSize) ? 7.5f : 8.5f);
        Font boldFont = FontFactory.getFont(FontFactory.COURIER_BOLD, "58".equals(normalizedPaperSize) ? 7.5f : 8.5f);
        Font totalFont = FontFactory.getFont(FontFactory.COURIER_BOLD, "58".equals(normalizedPaperSize) ? 9f : 10f);
        Font stampFont = FontFactory.getFont(FontFactory.COURIER_BOLD, "58".equals(normalizedPaperSize) ? 30f : 44f);

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(pageSize, mmToPoints(3f), mmToPoints(3f), mmToPoints(4f), mmToPoints(4f));
            PdfWriter writer = PdfWriter.getInstance(document, output);
            document.open();

            addCenteredLine(document, view.getCompanyName(), titleFont, 1f);
            addCenteredLine(document, view.getCompanyTagline(), bodyFont, 1f);
            addCenteredLine(document, view.getCompanyEmail(), bodyFont, 1f);
            addCenteredLine(document, "WA: " + safeText(view.getCompanyPhone()), bodyFont, 4f);

            PdfPTable statusTable = new PdfPTable(1);
            statusTable.setWidthPercentage(100);
            PdfPCell statusCell = new PdfPCell(new Phrase(safeText(view.getPaymentStatusLabel()), boldFont));
            statusCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            statusCell.setPaddingTop(4f);
            statusCell.setPaddingBottom(4f);
            statusTable.addCell(statusCell);
            statusTable.setSpacingAfter(6f);
            document.add(statusTable);

            PdfPTable infoTable = new PdfPTable(new float[]{1.5f, 3.5f});
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingAfter(6f);
            addInfoRow(infoTable, "No. Bukti", view.getInvoiceNumber(), bodyFont);
            addInfoRow(infoTable, "Tgl Bayar", view.getPaymentDateLabel(), bodyFont);
            addInfoRow(infoTable, "Pelanggan", view.getCustomerName(), bodyFont);
            addInfoRow(infoTable, "ID Pel", view.getCustomerCode(), bodyFont);
            addInfoRow(infoTable, "Metode", view.getPaymentMethodName(), bodyFont);
            document.add(infoTable);

            PdfPTable itemsTable = new PdfPTable(new float[]{3.4f, 1.6f});
            itemsTable.setWidthPercentage(100);
            itemsTable.setSpacingAfter(4f);
            addHeaderCell(itemsTable, "Deskripsi Layanan", boldFont, Element.ALIGN_LEFT);
            addHeaderCell(itemsTable, "Subtotal", boldFont, Element.ALIGN_RIGHT);
            for (InvoiceDocumentItemView item : view.getItems()) {
                PdfPCell descriptionCell = createBodyCell(itemDescription(item), bodyFont, Element.ALIGN_LEFT);
                PdfPCell subtotalCell = createBodyCell(item != null ? item.getSubtotalLabel() : null, bodyFont, Element.ALIGN_RIGHT);
                descriptionCell.setPaddingTop(4f);
                descriptionCell.setPaddingBottom(4f);
                subtotalCell.setPaddingTop(4f);
                subtotalCell.setPaddingBottom(4f);
                itemsTable.addCell(descriptionCell);
                itemsTable.addCell(subtotalCell);
            }
            document.add(itemsTable);

            PdfPTable summaryTable = new PdfPTable(new float[]{3f, 2f});
            summaryTable.setWidthPercentage(100);
            addSummaryRow(summaryTable, "Subtotal", view.getSubtotalLabel(), bodyFont);
            if (view.getTaxAmount() != null && view.getTaxAmount().signum() > 0) {
                addSummaryRow(summaryTable, "Pajak (" + support.formatRatePercent(view.getTaxRate()) + "%)", view.getTaxLabel(), bodyFont);
            }
            document.add(summaryTable);

            Paragraph total = new Paragraph("TOTAL BAYAR: " + safeText(view.getTotalLabel()), totalFont);
            total.setAlignment(Element.ALIGN_RIGHT);
            total.setSpacingBefore(4f);
            total.setSpacingAfter(6f);
            document.add(total);

            addServiceActiveCard(document, view, bodyFont, totalFont);
            if (support.hasText(view.getFooterNote()) && !InvoiceDocumentSupport.DEFAULT_TEXT.equals(view.getFooterNote())) {
                addCenteredLine(document, view.getFooterNote(), bodyFont, 1f);
            }
            addCenteredLine(document, "LAYANAN PELANGGAN 24 JAM", bodyFont, 0f);
            addPaidWatermark(writer, pageSize, view, stampFont);

            document.close();
            return output.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Gagal membuat PDF struk pembayaran.", ex);
        }
    }

    private LocalDate resolveServiceActiveUntil(Invoice invoice, LocalDate dueDate, LocalDate paymentDate) {
        LocalDate nextScheduledDueDate = findNextScheduledDueDate(invoice, dueDate, paymentDate);
        if (nextScheduledDueDate != null) {
            return nextScheduledDueDate;
        }

        if (dueDate != null) {
            return dueDate.plusMonths(1);
        }
        LocalDate billingMonth = support.safeDate(invoice.getBillingMonth(), null);
        if (billingMonth != null) {
            return billingMonth.plusMonths(1);
        }
        return paymentDate != null ? paymentDate.plusMonths(1) : null;
    }

    private LocalDate findNextScheduledDueDate(Invoice invoice, LocalDate dueDate, LocalDate paymentDate) {
        Customer customer = invoice.getCustomer();
        if (customer == null || customer.getId() == null) {
            return null;
        }

        LocalDate referenceDate = support.safeDate(
                dueDate,
                support.safeDate(invoice.getBillingMonth(), paymentDate)
        );
        if (referenceDate == null) {
            return null;
        }

        return invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customer.getId()).stream()
                .filter(candidate -> candidate.getId() != null && !candidate.getId().equals(invoice.getId()))
                .filter(candidate -> !"cancelled".equalsIgnoreCase(candidate.getStatus()))
                .map(candidate -> support.safeDate(candidate.getDueDate(), candidate.getBillingMonth()))
                .filter(candidateDate -> candidateDate != null && candidateDate.isAfter(referenceDate))
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    private CompanyProfile resolveCompany(Invoice invoice) {
        if (invoice.getCompanyProfile() != null) {
            return invoice.getCompanyProfile();
        }
        if (invoice.getCustomerService() != null
                && invoice.getCustomerService().getOdp() != null
                && invoice.getCustomerService().getOdp().getCompanyProfile() != null) {
            return invoice.getCustomerService().getOdp().getCompanyProfile();
        }
        try {
            return companyProfileRepository.findFirstByOrderByIdAsc()
                    .or(() -> companyProfileRepository.findByIsActiveTrue())
                    .orElse(null);
        } catch (DataAccessException ex) {
            if (isMissingRelation(ex, "company_profiles")) {
                return null;
            }
            throw ex;
        }
    }

    private CompanySetting resolveCompanySetting(CompanyProfile company) {
        try {
            if (company != null && company.getId() != null) {
                Optional<CompanySetting> companySetting = companySettingRepository.findFirstByCompanyProfileIdAndIsActiveTrueOrderByIdAsc(company.getId());
                if (companySetting.isPresent()) {
                    return companySetting.get();
                }
            }
            return companySettingRepository.findFirstByIsActiveTrueOrderByIdAsc().orElse(null);
        } catch (DataAccessException ex) {
            if (isMissingRelation(ex, "company_settings")) {
                return null;
            }
            throw ex;
        }
    }

    private BankAccount resolveBankAccount(Invoice invoice, CompanyProfile company) {
        if (invoice.getBankAccount() != null) {
            return invoice.getBankAccount();
        }
        try {
            if (company != null && company.getId() != null) {
                return bankAccountRepository.findFirstByCompanyProfileIdAndIsPrimaryTrueAndIsActiveTrueOrderByIdAsc(company.getId())
                        .or(() -> bankAccountRepository.findFirstByCompanyProfileIdAndIsActiveTrueOrderByIsPrimaryDescIdAsc(company.getId()))
                        .orElse(null);
            }
            return null;
        } catch (DataAccessException ex) {
            if (isMissingRelation(ex, "bank_accounts")) {
                return null;
            }
            throw ex;
        }
    }

    private PaymentMethod resolvePaymentMethod(Invoice invoice, BankAccount bankAccount) {
        if (invoice.getPaymentMethodEntity() != null) {
            return invoice.getPaymentMethodEntity();
        }
        return bankAccount != null ? bankAccount.getPaymentMethod() : null;
    }

    private void persistResolvedInvoiceContext(Invoice invoice, CompanyProfile company, BankAccount bankAccount) {
        if (invoice == null || invoice.getId() == null) {
            return;
        }

        boolean changed = false;
        if (invoice.getCompanyProfile() == null && company != null) {
            invoice.setCompanyProfile(company);
            changed = true;
        }

        if (invoice.getBankAccount() == null && bankAccount != null) {
            invoice.setBankAccount(bankAccount);
            changed = true;
        }

        if (changed) {
            invoiceRepository.save(invoice);
        }
    }

    private List<InvoiceDocumentItemView> buildItems(Invoice invoice, String currencyCode, String localeCode) {
        List<InvoiceItem> persistedItems;
        try {
            persistedItems = invoiceItemRepository.findByInvoiceIdOrderBySortOrderAscIdAsc(invoice.getId());
        } catch (DataAccessException ex) {
            if (isMissingRelation(ex, "invoice_items")) {
                persistedItems = List.of();
            } else {
                throw ex;
            }
        }
        List<InvoiceDocumentItemView> items = new ArrayList<>();

        if (!persistedItems.isEmpty()) {
            for (InvoiceItem item : persistedItems) {
                BigDecimal rate = support.safeNumber(item.getRate());
                BigDecimal subtotal = support.safeNumber(item.getSubtotal());
                items.add(InvoiceDocumentItemView.builder()
                        .description(support.safeValue(item.getDescription()))
                        .rate(rate)
                        .quantity(support.safeNumber(item.getQuantity()))
                        .unitName(support.safeValue(item.getUnitName()))
                        .subtotal(subtotal)
                        .rateLabel(support.formatMoney(rate, currencyCode, localeCode))
                        .subtotalLabel(support.formatMoney(subtotal, currencyCode, localeCode))
                        .build());
            }
        } else {
            addLegacyItem(items, resolveSubscriptionDescription(invoice), invoice.getMonthlyFee(), "Bulan", currencyCode, localeCode);
            addLegacyItem(items, "Biaya Instalasi", invoice.getInstallationFee(), "Layanan", currencyCode, localeCode);
            addLegacyItem(items, "Biaya Tambahan", invoice.getOtherCharges(), "Layanan", currencyCode, localeCode);
        }

        if (items.isEmpty()) {
            items.add(placeholderItem(currencyCode, localeCode));
        }
        return items;
    }

    private void addLegacyItem(List<InvoiceDocumentItemView> items, String description, BigDecimal amount, String unitName, String currencyCode, String localeCode) {
        BigDecimal normalized = support.safeNumber(amount);
        if (normalized.signum() <= 0) {
            return;
        }
        items.add(InvoiceDocumentItemView.builder()
                .description(description)
                .rate(normalized)
                .quantity(BigDecimal.ONE)
                .unitName(unitName)
                .subtotal(normalized)
                .rateLabel(support.formatMoney(normalized, currencyCode, localeCode))
                .subtotalLabel(support.formatMoney(normalized, currencyCode, localeCode))
                .build());
    }

    private InvoiceDocumentItemView placeholderItem(String currencyCode, String localeCode) {
        return InvoiceDocumentItemView.builder()
                .description(InvoiceDocumentSupport.DEFAULT_TEXT)
                .rate(BigDecimal.ZERO)
                .quantity(BigDecimal.ONE)
                .unitName(InvoiceDocumentSupport.DEFAULT_TEXT)
                .subtotal(BigDecimal.ZERO)
                .rateLabel(support.formatMoney(BigDecimal.ZERO, currencyCode, localeCode))
                .subtotalLabel(support.formatMoney(BigDecimal.ZERO, currencyCode, localeCode))
                .build();
    }

    private BigDecimal resolveSubtotal(Invoice invoice, List<InvoiceDocumentItemView> items) {
        if (invoice.getSubtotalAmount() != null) {
            return support.safeNumber(invoice.getSubtotalAmount());
        }
        BigDecimal fromItems = items.stream()
                .map(InvoiceDocumentItemView::getSubtotal)
                .map(support::safeNumber)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (fromItems.signum() > 0) {
            return fromItems;
        }
        return support.safeNumber(invoice.getMonthlyFee())
                .add(support.safeNumber(invoice.getInstallationFee()))
                .add(support.safeNumber(invoice.getOtherCharges()));
    }

    private BigDecimal resolveTaxRate(Invoice invoice, CompanySetting settings) {
        if (invoice.getTaxRate() != null) {
            return invoice.getTaxRate();
        }
        return settings != null ? support.safeNumber(settings.getDefaultTaxRate()) : BigDecimal.ZERO;
    }

    private BigDecimal resolveTaxAmount(Invoice invoice, BigDecimal subtotal, BigDecimal taxRate) {
        if (invoice.getTaxAmount() != null) {
            return support.safeNumber(invoice.getTaxAmount());
        }
        if (invoice.getTotalAmount() != null && invoice.getTotalAmount().compareTo(subtotal) > 0 && taxRate.signum() == 0) {
            return invoice.getTotalAmount().subtract(subtotal).max(BigDecimal.ZERO);
        }
        return subtotal.multiply(taxRate)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveTotalAmount(Invoice invoice, BigDecimal subtotal, BigDecimal taxAmount) {
        if (invoice.getTotalAmount() != null) {
            return support.safeNumber(invoice.getTotalAmount());
        }
        return subtotal.add(taxAmount);
    }

    private String resolveDocumentTitle(CompanySetting settings) {
        return settings != null && support.hasText(settings.getDefaultInvoiceTitle())
                ? settings.getDefaultInvoiceTitle()
                : "INVOICE";
    }

    private String resolveDocumentSubtitle(Invoice invoice, CompanySetting settings) {
        if (support.hasText(invoice.getDocumentSubtitle())) {
            return invoice.getDocumentSubtitle();
        }
        if (settings != null && support.hasText(settings.getDefaultInvoiceSubtitle())) {
            return settings.getDefaultInvoiceSubtitle();
        }
        return "Document Payment Information";
    }

    private String resolvePaymentMethodName(PaymentMethod paymentMethod, Invoice invoice) {
        if (paymentMethod != null && support.hasText(paymentMethod.getName())) {
            return paymentMethod.getName();
        }
        if (support.hasText(invoice.getPaymentMethod())) {
            return prettifyLabel(invoice.getPaymentMethod());
        }
        return InvoiceDocumentSupport.DEFAULT_TEXT;
    }

    private String resolvePaymentStatusLabel(Invoice invoice) {
        String status = support.hasText(invoice.getStatus()) ? invoice.getStatus().trim().toLowerCase(Locale.ROOT) : "";
        return switch (status) {
            case "paid" -> "LUNAS / PAID";
            case "overdue" -> "JATUH TEMPO / OVERDUE";
            case "cancelled" -> "DIBATALKAN / CANCELLED";
            default -> "BELUM LUNAS / UNPAID";
        };
    }

    private String resolvePaymentReferenceLabel(BankAccount bankAccount) {
        if (bankAccount != null && support.hasText(bankAccount.getPaymentReferenceLabel())) {
            return bankAccount.getPaymentReferenceLabel();
        }
        return "Payment Reference";
    }

    private String resolveFooterNote(Invoice invoice, CompanySetting settings) {
        if (support.hasText(invoice.getNotes())) {
            return invoice.getNotes();
        }
        if (settings != null && support.hasText(settings.getDefaultFooterNote())) {
            return settings.getDefaultFooterNote();
        }
        return InvoiceDocumentSupport.DEFAULT_TEXT;
    }

    private String resolveSubscriptionDescription(Invoice invoice) {
        String invoiceType = support.hasText(invoice.getInvoiceType()) ? invoice.getInvoiceType().trim().toLowerCase(Locale.ROOT) : "subscription";
        if ("activation".equals(invoiceType)) {
            return "Biaya Aktivasi Pelanggan";
        }
        return "Biaya Langganan Bulanan";
    }

    private String buildCompanyAddress(CompanyProfile company) {
        if (company == null) {
            return InvoiceDocumentSupport.DEFAULT_TEXT;
        }
        return support.joinNonBlank(", ",
                company.getAddress(),
                company.getDistrictName(),
                company.getRegencyName(),
                company.getProvinceName()
        );
    }

    private String buildPaymentAddress(BankAccount bankAccount) {
        if (bankAccount == null) {
            return InvoiceDocumentSupport.DEFAULT_TEXT;
        }
        return support.joinNonBlank(", ",
                bankAccount.getBranchAddress(),
                bankAccount.getInstructions(),
                bankAccount.getSwiftCode()
        );
    }

    private String buildCustomerAddress(Customer customer, CustomerServiceEntity service) {
        if (customer == null) {
            return InvoiceDocumentSupport.DEFAULT_TEXT;
        }
        return support.joinNonBlank(", ",
                customer.getInstallationAddress(),
                service != null && service.getOdp() != null ? service.getOdp().getLocation() : null
        );
    }

    private String formatPaymentAccount(BankAccount bankAccount) {
        if (bankAccount == null) {
            return null;
        }
        return support.joinNonBlank(" - ", bankAccount.getBankName(), bankAccount.getAccountNumber());
    }

    private String prettifyLabel(String rawValue) {
        if (!support.hasText(rawValue)) {
            return InvoiceDocumentSupport.DEFAULT_TEXT;
        }
        String normalized = rawValue.trim().replace('_', ' ').replace('-', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1).toLowerCase(Locale.ROOT);
    }

    private Rectangle buildThermalPageSize(String paperSize, InvoiceDocumentView view) {
        float widthMm = "58".equals(paperSize) ? 58f : 80f;
        float baseHeightMm = "58".equals(paperSize) ? 120f : 130f;
        int itemCount = view.getItems() != null ? view.getItems().size() : 0;
        float itemExtraMm = itemCount * ("58".equals(paperSize) ? 12f : 10f);
        float noteExtraMm = estimateExtraHeightMm(view.getFooterNote(), "58".equals(paperSize) ? 26 : 38);
        float totalHeightMm = Math.max(baseHeightMm + itemExtraMm + noteExtraMm, "58".equals(paperSize) ? 150f : 165f);
        return new Rectangle(mmToPoints(widthMm), mmToPoints(totalHeightMm));
    }

    private float estimateExtraHeightMm(String text, int charsPerLine) {
        if (!support.hasText(text) || InvoiceDocumentSupport.DEFAULT_TEXT.equals(text)) {
            return 0f;
        }
        int lines = Math.max(1, (int) Math.ceil((double) text.length() / Math.max(1, charsPerLine)));
        return lines * 4.5f;
    }

    private float mmToPoints(float mm) {
        return (mm / 25.4f) * 72f;
    }

    private void addCenteredLine(Document document, String text, Font font, float spacingAfter) throws Exception {
        if (!support.hasText(text) || InvoiceDocumentSupport.DEFAULT_TEXT.equals(text)) {
            return;
        }
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setAlignment(Element.ALIGN_CENTER);
        paragraph.setSpacingAfter(spacingAfter);
        document.add(paragraph);
    }

    private void addInfoRow(PdfPTable table, String label, String value, Font font) {
        table.addCell(createBodyCell(label, font, Element.ALIGN_LEFT));
        table.addCell(createBodyCell(": " + safeText(value), font, Element.ALIGN_LEFT));
    }

    private void addHeaderCell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(safeText(text), font));
        cell.setHorizontalAlignment(alignment);
        cell.setBorderWidthBottom(0.8f);
        cell.setBorderWidthTop(0f);
        cell.setBorderWidthLeft(0f);
        cell.setBorderWidthRight(0f);
        cell.setPaddingTop(3f);
        cell.setPaddingBottom(4f);
        table.addCell(cell);
    }

    private void addSummaryRow(PdfPTable table, String label, String value, Font font) {
        table.addCell(createBodyCell(label, font, Element.ALIGN_LEFT));
        table.addCell(createBodyCell(value, font, Element.ALIGN_RIGHT));
    }

    private void addServiceActiveCard(Document document, InvoiceDocumentView view, Font labelFont, Font dateFont) throws Exception {
        if (!support.hasText(view.getServiceActiveUntilLabel()) || InvoiceDocumentSupport.DEFAULT_TEXT.equals(view.getServiceActiveUntilLabel())) {
            return;
        }

        PdfPTable card = new PdfPTable(1);
        card.setWidthPercentage(100);
        card.setSpacingBefore(4f);
        card.setSpacingAfter(6f);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(new Color(244, 244, 244));
        cell.setPaddingTop(6f);
        cell.setPaddingBottom(8f);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        Paragraph label = new Paragraph("LAYANAN ANDA AKTIF S/D:", labelFont);
        label.setAlignment(Element.ALIGN_CENTER);
        label.setSpacingAfter(2f);
        cell.addElement(label);

        Paragraph date = new Paragraph(safeText(view.getServiceActiveUntilLabel()).toUpperCase(Locale.forLanguageTag("id-ID")), dateFont);
        date.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(date);

        card.addCell(cell);
        document.add(card);
    }

    private void addPaidWatermark(PdfWriter writer, Rectangle pageSize, InvoiceDocumentView view, Font stampFont) {
        if (writer == null || view == null || !support.hasText(view.getPaymentStatusLabel())) {
            return;
        }
        if (!view.getPaymentStatusLabel().toLowerCase(Locale.ROOT).contains("lunas")) {
            return;
        }

        final Color stampColor = new Color(156, 64, 64);
        final float centerX = pageSize.getWidth() / 2f;
        final float centerY = pageSize.getHeight() / 2f;
        final float rotation = 28f;
        final float outerWidth = Math.min(pageSize.getWidth() * 0.72f, mmToPoints(54f));
        final float outerHeight = Math.min(pageSize.getHeight() * 0.22f, mmToPoints(18f));
        final float innerInset = 3.5f;

        PdfContentByte over = writer.getDirectContent();
        PdfGState state = new PdfGState();
        state.setFillOpacity(0.16f);
        state.setStrokeOpacity(0.28f);
        over.saveState();
        over.setGState(state);
        over.setColorStroke(stampColor);
        over.setColorFill(stampColor);
        over.setLineWidth(2f);

        double radians = Math.toRadians(rotation);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        over.concatCTM(cos, sin, -sin, cos, centerX, centerY);
        over.roundRectangle(-outerWidth / 2f, -outerHeight / 2f, outerWidth, outerHeight, 8f);
        over.stroke();

        over.setLineWidth(1f);
        over.roundRectangle(
                (-outerWidth / 2f) + innerInset,
                (-outerHeight / 2f) + innerInset,
                outerWidth - (innerInset * 2f),
                outerHeight - (innerInset * 2f),
                6f
        );
        over.stroke();

        Font paidStampFont = new Font(stampFont);
        paidStampFont.setColor(stampColor);
        ColumnText.showTextAligned(
                over,
                Element.ALIGN_CENTER,
                new Phrase("LUNAS", paidStampFont),
                0f,
                -(paidStampFont.getSize() * 0.22f),
                0f
        );
        over.restoreState();
    }

    private PdfPCell createBodyCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(safeText(text), font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_TOP);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(2f);
        cell.setPaddingBottom(2f);
        cell.setPaddingLeft(0f);
        cell.setPaddingRight(0f);
        return cell;
    }

    private String itemDescription(InvoiceDocumentItemView item) {
        if (item == null) {
            return InvoiceDocumentSupport.DEFAULT_TEXT;
        }
        String description = safeText(item.getDescription());
        String unitName = safeText(item.getUnitName());
        if (InvoiceDocumentSupport.DEFAULT_TEXT.equals(unitName)) {
            return description;
        }
        String quantity = item.getQuantity() != null ? item.getQuantity().stripTrailingZeros().toPlainString() : "1";
        return description + "\nQty: " + quantity + " " + unitName;
    }

    private String safeText(String value) {
        return support.safeValue(value);
    }

    private boolean isMissingRelation(Throwable throwable, String relationName) {
        String normalizedRelation = relationName != null ? relationName.toLowerCase(Locale.ROOT) : "";
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("relation \"" + normalizedRelation + "\" does not exist")
                        || normalized.contains("table \"" + normalizedRelation + "\" does not exist")
                        || normalized.contains(normalizedRelation + " does not exist")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
