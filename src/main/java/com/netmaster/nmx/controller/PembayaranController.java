package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.HistoryPaymentRowDTO;
import com.netmaster.nmx.service.PaymentManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pembayaran")
@RequiredArgsConstructor
public class PembayaranController {

    private final PaymentManagementService paymentManagementService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<HistoryPaymentRowDTO>>> getPembayaran(
            @RequestParam(name = "status", defaultValue = "ALL") String status
    ) {
        try {
            List<HistoryPaymentRowDTO> rows = paymentManagementService.getCustomerHistoryRows(status);
            return ResponseEntity.ok(ApiResponse.success("Data pembayaran berhasil diambil", rows));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }
}
