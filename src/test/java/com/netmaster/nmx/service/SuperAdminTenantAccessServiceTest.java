package com.netmaster.nmx.service;

import com.netmaster.nmx.dto.superadmin.TenantDirectoryResponse;
import com.netmaster.nmx.master.repository.SubscriptionPlanRepository;
import com.netmaster.nmx.master.repository.TenantRepository;
import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.InvoiceRepository;
import com.netmaster.nmx.repository.NetworkDeviceRepository;
import com.netmaster.nmx.repository.ProjectRecordRepository;
import com.netmaster.nmx.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminTenantAccessServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private SubscriptionPlanRepository subscriptionPlanRepository;

    @Mock
    private TenantConnectionManager tenantConnectionManager;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private NetworkDeviceRepository networkDeviceRepository;

    @Mock
    private ProjectRecordRepository projectRecordRepository;

    @Mock
    private MasterAuditLogService auditLogService;

    @Mock
    private JdbcTemplate masterJdbcTemplate;

    @InjectMocks
    private SuperAdminTenantAccessService tenantAccessService;

    @Test
    void restoreTenant_shouldRestoreDeletedTenantAsActiveWhenApprovedAtExists() {
        when(masterJdbcTemplate.queryForMap(any(String.class), eq(15L))).thenReturn(Map.of(
                "id", 15L,
                "company_name", "Fiber Kita",
                "slug", "fiber-kita",
                "approved_at", Timestamp.valueOf(LocalDateTime.of(2026, 4, 4, 10, 15))
        ));
        when(masterJdbcTemplate.update(any(String.class), eq("ACTIVE"), eq(15L))).thenReturn(1);

        tenantAccessService.restoreTenant(15L, 3L, "127.0.0.1");

        verify(masterJdbcTemplate).update(any(String.class), eq("ACTIVE"), eq(15L));
        verify(auditLogService).record(
                eq("SUPERADMIN"),
                eq(3L),
                eq("TENANT_RESTORED"),
                eq(15L),
                eq("TENANT"),
                eq("15"),
                any(Map.class),
                eq("127.0.0.1")
        );
    }

    @Test
    void restoreTenant_shouldRestoreDeletedTenantAsPendingWhenNeverApproved() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("id", 18L);
        snapshot.put("company_name", "Pending Net");
        snapshot.put("slug", "pending-net");
        snapshot.put("approved_at", null);
        when(masterJdbcTemplate.queryForMap(any(String.class), eq(18L))).thenReturn(snapshot);
        when(masterJdbcTemplate.update(any(String.class), eq("PENDING"), eq(18L))).thenReturn(1);

        tenantAccessService.restoreTenant(18L, 4L, "127.0.0.1");

        verify(masterJdbcTemplate).update(any(String.class), eq("PENDING"), eq(18L));
    }

    @Test
    void findDeletedTenants_shouldReturnRowsFromRecycleBinQuery() {
        TenantDirectoryResponse deleted = TenantDirectoryResponse.builder()
                .id(21L)
                .companyName("Deleted ISP")
                .status("DELETED")
                .build();
        when(masterJdbcTemplate.query(any(String.class), any(org.springframework.jdbc.core.RowMapper.class)))
                .thenReturn(List.of(deleted));

        List<TenantDirectoryResponse> result = tenantAccessService.findDeletedTenants();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getStatus()).isEqualTo("DELETED");
    }
}
