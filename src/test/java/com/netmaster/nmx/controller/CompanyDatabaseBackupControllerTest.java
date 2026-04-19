package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.repository.CompanyProfileRepository;
import com.netmaster.nmx.service.CompanyBankAccountService;
import com.netmaster.nmx.service.CompanyLogoStorageService;
import com.netmaster.nmx.service.DatabaseBackupService;
import com.netmaster.nmx.service.IndonesiaRegionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyDatabaseBackupControllerTest {

    @Mock
    private CompanyProfileRepository companyRepository;

    @Mock
    private IndonesiaRegionService regionService;

    @Mock
    private CompanyLogoStorageService companyLogoStorageService;

    @Mock
    private CompanyBankAccountService companyBankAccountService;

    @Mock
    private DatabaseBackupService databaseBackupService;

    @InjectMocks
    private CompanyController companyController;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void exportDatabaseBackup_returnsAttachment() {
        authenticateAsSuperAdmin();
        when(databaseBackupService.exportBackup()).thenReturn(
                new DatabaseBackupService.BackupFile(
                        "nmx-database-backup-20260406-160000.json",
                        "{\"ok\":true}".getBytes(),
                        12,
                        345L
                )
        );

        ResponseEntity<byte[]> response = companyController.exportDatabaseBackup();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"nmx-database-backup-20260406-160000.json\"");
        assertThat(response.getHeaders().getFirst("X-NMX-Backup-Tables")).isEqualTo("12");
        assertThat(response.getHeaders().getFirst("X-NMX-Backup-Rows")).isEqualTo("345");
        assertThat(response.getBody()).isEqualTo("{\"ok\":true}".getBytes());
    }

    @Test
    void importDatabaseBackup_returnsRestoreSummary() {
        authenticateAsSuperAdmin();
        when(databaseBackupService.importBackup(any())).thenReturn(
                new DatabaseBackupService.BackupImportResult(12, 345L, "2026-04-06T16:00:00+08:00")
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "backup.json",
                "application/json",
                "{\"version\":1}".getBytes()
        );

        ResponseEntity<ApiResponse<Map<String, Object>>> response = companyController.importDatabaseBackup(file);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).isEqualTo("Import database berhasil diproses");
        assertThat(response.getBody().getData()).containsEntry("tables", 12);
        assertThat(response.getBody().getData()).containsEntry("rows", 345L);
        assertThat(response.getBody().getData()).containsEntry("sourceCreatedAt", "2026-04-06T16:00:00+08:00");
    }

    private void authenticateAsSuperAdmin() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "integration-admin",
                        "secret",
                        List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
                )
        );
    }
}
