package com.netmaster.nmx.controller;

import com.netmaster.nmx.config.TenantProvisioningProperties;
import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.tenant.TenantDashboardResponse;
import com.netmaster.nmx.master.model.Tenant;
import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.InvoiceRepository;
import com.netmaster.nmx.repository.NetworkDeviceRepository;
import com.netmaster.nmx.repository.TicketRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TenantDashboardController {

    private final TenantProvisioningProperties tenantProvisioningProperties;
    private final CustomerRepository customerRepository;
    private final InvoiceRepository invoiceRepository;
    private final TicketRepository ticketRepository;
    private final NetworkDeviceRepository networkDeviceRepository;

    @GetMapping("/tenant/api/dashboard")
    public ResponseEntity<ApiResponse<TenantDashboardResponse>> dashboard(HttpServletRequest request) {
        if (!tenantProvisioningProperties.isEnabled()) {
            return ResponseEntity.status(503).body(ApiResponse.error("Fitur tenant dinonaktifkan sementara."));
        }
        Tenant tenant = (Tenant) request.getAttribute("resolvedTenant");
        TenantDashboardResponse response = TenantDashboardResponse.builder()
                .tenantSlug(tenant.getSlug())
                .tenantName(tenant.getCompanyName())
                .tenantStatus(tenant.getStatus().name())
                .totalCustomers(customerRepository.count())
                .totalInvoices(invoiceRepository.count())
                .totalTickets(ticketRepository.count())
                .totalDevices(networkDeviceRepository.count())
                .build();
        return ResponseEntity.ok(ApiResponse.success("Dashboard tenant berhasil diambil.", response));
    }
}
