package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.InvoiceQrCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceQrCodeRepository extends JpaRepository<InvoiceQrCode, Long> {

    Optional<InvoiceQrCode> findFirstByInvoiceIdAndIsActiveTrueOrderByUpdatedAtDescIdDesc(Long invoiceId);
}
