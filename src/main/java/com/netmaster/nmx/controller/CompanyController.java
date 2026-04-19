package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.BankAccountRequest;
import com.netmaster.nmx.dto.BankAccountView;
import com.netmaster.nmx.dto.RegionOption;
import com.netmaster.nmx.model.CompanyProfile;
import com.netmaster.nmx.repository.CompanyProfileRepository;
import com.netmaster.nmx.security.TenantRoleAccess;
import com.netmaster.nmx.service.CompanyBankAccountService;
import com.netmaster.nmx.service.DatabaseBackupService;
import com.netmaster.nmx.service.CompanyLogoStorageService;
import com.netmaster.nmx.service.IndonesiaRegionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/company")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyProfileRepository companyRepository;
    private final IndonesiaRegionService regionService;
    private final CompanyLogoStorageService companyLogoStorageService;
    private final CompanyBankAccountService companyBankAccountService;
    private final DatabaseBackupService databaseBackupService;

    private boolean hasPermission(String permission) {
        return switch (permission) {
            case "READ" -> TenantRoleAccess.canRead(SecurityContextHolder.getContext().getAuthentication());
            case "WRITE" -> TenantRoleAccess.canDelete(SecurityContextHolder.getContext().getAuthentication());
            default -> false;
        };
    }

    // Get company profile
    @GetMapping
    public ResponseEntity<ApiResponse<CompanyProfile>> getCompanyProfile() {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        CompanyProfile company = findPrimaryCompany().orElse(null);
        if (company == null) {
            return ResponseEntity.ok(ApiResponse.success("Company profile", null));
        }
        return ResponseEntity.ok(ApiResponse.success("Company profile found", company));
    }

    // Get company by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyProfile>> getCompanyById(@PathVariable Long id) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        return companyRepository.findById(id)
                .map(company -> ResponseEntity.ok(ApiResponse.success("Company found", company)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("Company not found")));
    }

    // Create or update company profile
    @PostMapping
    public ResponseEntity<ApiResponse<CompanyProfile>> saveCompanyProfile(@RequestBody CompanyProfile request) {
        if (!hasPermission("WRITE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Tenant Super Admin only"));
        }
        try {
            CompanyProfile company = findPrimaryCompany().orElseGet(CompanyProfile::new);
            applyCompanyData(company, request);
            CompanyProfile saved = companyRepository.save(company);
            return ResponseEntity.ok(ApiResponse.success("Company profile saved", saved));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    // Update company profile
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyProfile>> updateCompanyProfile(@PathVariable Long id, @RequestBody CompanyProfile request) {
        if (!hasPermission("WRITE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Tenant Super Admin only"));
        }
        CompanyProfile company = companyRepository.findById(id).orElse(null);
        if (company == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Company not found"));
        }
        try {
            applyCompanyData(company, request);
            CompanyProfile updated = companyRepository.save(company);
            return ResponseEntity.ok(ApiResponse.success("Company profile updated", updated));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<CompanyProfile>> uploadCompanyLogo(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        if (!hasPermission("WRITE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Tenant Super Admin only"));
        }

        CompanyProfile company = companyRepository.findById(id).orElse(null);
        if (company == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Company not found"));
        }

        try {
            String storedLogo = companyLogoStorageService.storeLogo(file, company.getLogo());
            company.setLogo(storedLogo);
            CompanyProfile saved = companyRepository.save(company);
            return ResponseEntity.ok(ApiResponse.success("Logo company berhasil diupload", saved));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/logo/{filename:.+}")
    public ResponseEntity<Resource> getCompanyLogo(@PathVariable String filename) {
        Resource resource = companyLogoStorageService.loadAsResource(filename);
        MediaType mediaType = companyLogoStorageService.detectMediaType(filename);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }

    // Delete company profile
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCompanyProfile(@PathVariable Long id) {
        if (!hasPermission("WRITE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Tenant Super Admin only"));
        }
        CompanyProfile company = companyRepository.findById(id).orElse(null);
        if (company == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Company not found"));
        }
        companyLogoStorageService.deleteIfExists(company.getLogo());
        companyRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Company profile deleted", null));
    }

    @GetMapping("/regions/provinces")
    public ResponseEntity<ApiResponse<List<RegionOption>>> getProvinces() {
        try {
            return ResponseEntity.ok(ApiResponse.success("Data provinsi berhasil diambil", regionService.getProvinces()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/regions/regencies")
    public ResponseEntity<ApiResponse<List<RegionOption>>> getRegencies(@RequestParam String provinceCode) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Data kabupaten/kota berhasil diambil", regionService.getRegencies(provinceCode)));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/regions/districts")
    public ResponseEntity<ApiResponse<List<RegionOption>>> getDistricts(@RequestParam String regencyCode) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Data kecamatan berhasil diambil", regionService.getDistricts(regencyCode)));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/regions/villages")
    public ResponseEntity<ApiResponse<List<RegionOption>>> getVillages(@RequestParam String districtCode) {
        try {
            return ResponseEntity.ok(ApiResponse.success("Data kelurahan/desa berhasil diambil", regionService.getVillages(districtCode)));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/{id}/bank-accounts")
    public ResponseEntity<ApiResponse<List<BankAccountView>>> getBankAccounts(@PathVariable Long id) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Data rekening bank berhasil diambil",
                companyBankAccountService.getAccounts(id)
        ));
    }

    @PostMapping("/{id}/bank-accounts")
    public ResponseEntity<ApiResponse<BankAccountView>> createBankAccount(@PathVariable Long id, @Valid @RequestBody BankAccountRequest request) {
        if (!hasPermission("WRITE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak - Tenant Super Admin only"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Rekening bank berhasil ditambahkan",
                    companyBankAccountService.createAccount(id, request)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PutMapping("/{id}/bank-accounts/{accountId}")
    public ResponseEntity<ApiResponse<BankAccountView>> updateBankAccount(
            @PathVariable Long id,
            @PathVariable Long accountId,
            @Valid @RequestBody BankAccountRequest request) {
        if (!hasPermission("WRITE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak - Tenant Super Admin only"));
        }
        try {
            return ResponseEntity.ok(ApiResponse.success(
                    "Rekening bank berhasil diperbarui",
                    companyBankAccountService.updateAccount(id, accountId, request)
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}/bank-accounts/{accountId}")
    public ResponseEntity<ApiResponse<Void>> deleteBankAccount(@PathVariable Long id, @PathVariable Long accountId) {
        if (!hasPermission("WRITE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Akses ditolak - Tenant Super Admin only"));
        }
        try {
            companyBankAccountService.deleteAccount(id, accountId);
            return ResponseEntity.ok(ApiResponse.success("Rekening bank berhasil dihapus", null));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/database/export")
    public ResponseEntity<byte[]> exportDatabaseBackup() {
        if (!hasPermission("WRITE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        DatabaseBackupService.BackupFile backup = databaseBackupService.exportBackup();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + backup.getFilename() + "\"")
                .header("X-NMX-Backup-Tables", String.valueOf(backup.getTableCount()))
                .header("X-NMX-Backup-Rows", String.valueOf(backup.getRowCount()))
                .body(backup.getContent());
    }

    @PostMapping(value = "/database/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> importDatabaseBackup(@RequestParam("file") MultipartFile file) {
        if (!hasPermission("WRITE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Super Admin only"));
        }

        try {
            DatabaseBackupService.BackupImportResult result = databaseBackupService.importBackup(file);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("tables", result.getTableCount());
            data.put("rows", result.getRowCount());
            data.put("sourceCreatedAt", result.getSourceCreatedAt());
            return ResponseEntity.ok(ApiResponse.success("Import database berhasil diproses", data));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    private Optional<CompanyProfile> findPrimaryCompany() {
        return companyRepository.findFirstByOrderByIdAsc()
                .or(() -> companyRepository.findByIsActiveTrue());
    }

    private void applyCompanyData(CompanyProfile target, CompanyProfile source) {
        String companyName = clean(source.getName());
        if (!hasText(companyName)) {
            throw new IllegalArgumentException("Nama perusahaan wajib diisi");
        }

        target.setName(companyName);
        target.setPhone(clean(source.getPhone()));
        target.setEmail(clean(source.getEmail()));
        target.setWebsite(clean(source.getWebsite()));
        target.setTagline(clean(source.getTagline()));
        target.setNpwp(clean(source.getNpwp()));
        target.setPkpNumber(clean(source.getPkpNumber()));
        target.setBusinessType(clean(source.getBusinessType()));
        target.setFacebook(clean(source.getFacebook()));
        target.setInstagram(clean(source.getInstagram()));
        target.setTwitter(clean(source.getTwitter()));
        target.setWhatsapp(clean(source.getWhatsapp()));
        target.setSupportEmail(clean(source.getSupportEmail()));

        target.setProvinceCode(clean(source.getProvinceCode()));
        target.setProvinceName(clean(source.getProvinceName()));
        target.setRegencyCode(clean(source.getRegencyCode()));
        target.setRegencyName(clean(source.getRegencyName()));
        target.setDistrictCode(clean(source.getDistrictCode()));
        target.setDistrictName(clean(source.getDistrictName()));
        target.setVillageCode(clean(source.getVillageCode()));
        target.setVillageName(clean(source.getVillageName()));
        target.setRt(clean(source.getRt()));
        target.setRw(clean(source.getRw()));
        target.setBuildingNumber(clean(source.getBuildingNumber()));
        target.setStreetName(clean(source.getStreetName()));
        target.setGoogleMapsCoordinates(clean(source.getGoogleMapsCoordinates()));
        target.setAddress(buildAddress(target, clean(source.getAddress())));
        target.setActive(true);
    }

    private String buildAddress(CompanyProfile company, String fallbackAddress) {
        List<String> parts = new ArrayList<>();

        if (hasText(company.getStreetName())) {
            parts.add("Jl. " + company.getStreetName());
        }
        if (hasText(company.getBuildingNumber())) {
            parts.add("No. " + company.getBuildingNumber());
        }

        String rtRw = buildRtRw(company.getRt(), company.getRw());
        if (hasText(rtRw)) {
            parts.add(rtRw);
        }
        if (hasText(company.getVillageName())) {
            parts.add("Kel/Desa " + company.getVillageName());
        }
        if (hasText(company.getDistrictName())) {
            parts.add("Kec. " + company.getDistrictName());
        }
        if (hasText(company.getRegencyName())) {
            parts.add(company.getRegencyName());
        }
        if (hasText(company.getProvinceName())) {
            parts.add(company.getProvinceName());
        }

        if (!parts.isEmpty()) {
            if (hasText(fallbackAddress)) {
                parts.add(fallbackAddress);
            }
            return String.join(", ", parts);
        }

        return fallbackAddress;
    }

    private String buildRtRw(String rt, String rw) {
        if (hasText(rt) && hasText(rw)) {
            return "RT " + rt + " / RW " + rw;
        }
        if (hasText(rt)) {
            return "RT " + rt;
        }
        if (hasText(rw)) {
            return "RW " + rw;
        }
        return null;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
