package com.netmaster.nmx.service;

import com.netmaster.nmx.model.Invoice;
import com.netmaster.nmx.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class InvoiceNumberService {

    private final InvoiceRepository invoiceRepository;

    public String generate() {
        String year = String.valueOf(LocalDate.now().getYear());
        String prefix = "INV-" + year + "-";

        int nextSequence = invoiceRepository.findTopByInvoiceNumberStartingWithOrderByInvoiceNumberDesc(prefix)
                .map(Invoice::getInvoiceNumber)
                .map(number -> number.substring(prefix.length()))
                .map(Integer::parseInt)
                .orElse(0) + 1;

        return prefix + String.format("%04d", nextSequence);
    }
}
