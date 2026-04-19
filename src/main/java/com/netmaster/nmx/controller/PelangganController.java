package com.netmaster.nmx.controller;

import com.netmaster.nmx.dto.ApiResponse;
import com.netmaster.nmx.dto.CustomerActivationRequest;
import com.netmaster.nmx.dto.CustomerBasicUpdateDTO;
import com.netmaster.nmx.dto.CustomerDataRowDTO;
import com.netmaster.nmx.dto.CustomerEditDetailDTO;
import com.netmaster.nmx.dto.CustomerRegistrationDTO;
import com.netmaster.nmx.dto.InvoiceDTO;
import com.netmaster.nmx.dto.InvoiceRowDTO;
import com.netmaster.nmx.dto.OdcOptionView;
import com.netmaster.nmx.dto.OdpOptionView;
import com.netmaster.nmx.model.*;
import com.netmaster.nmx.security.TenantRoleAccess;
import com.netmaster.nmx.service.ICustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/pelanggan")
@RequiredArgsConstructor
public class PelangganController {

    private final ICustomerService customerService;

    // Check if current user has permission
    private boolean hasPermission(String permission) {
        return switch (permission) {
            case "CREATE", "READ" -> TenantRoleAccess.canRead(SecurityContextHolder.getContext().getAuthentication());
            case "UPDATE" -> TenantRoleAccess.canWrite(SecurityContextHolder.getContext().getAuthentication());
            case "DELETE" -> TenantRoleAccess.canDelete(SecurityContextHolder.getContext().getAuthentication());
            default -> false;
        };
    }

    // Get current user's permission level
    private String getCurrentUserPermissionLevel() {
        return TenantRoleAccess.permissionLevel(SecurityContextHolder.getContext().getAuthentication());
    }

    // ==================== THYMELEAF PAGES ====================

    @GetMapping("/paket")
    public String paketPage(Model model) {
        if (!hasPermission("READ")) {
            return "redirect:/dashboard?access_denied";
        }
        
        model.addAttribute("permissionLevel", getCurrentUserPermissionLevel());
        model.addAttribute("page", "paket");
        return "layout/base";
    }

    @GetMapping("/registrasi")
    public String registrasiPage(Model model) {
        if (!hasPermission("READ")) {
            return "redirect:/dashboard?access_denied";
        }
        
        // Pre-load data for dropdowns
        model.addAttribute("regions", customerService.getAllRegions());
        model.addAttribute("servers", customerService.getAllServers());
        model.addAttribute("odcs", customerService.getAllOdcs());
        model.addAttribute("odps", customerService.getAllOdps());
        model.addAttribute("packages", customerService.getAllPackages());
        model.addAttribute("serviceTypes", customerService.getAllServiceTypes());
        model.addAttribute("technicians", customerService.getAllTechnicians());
        model.addAttribute("permissionLevel", getCurrentUserPermissionLevel());
        model.addAttribute("page", "registrasi");
        return "layout/base";
    }

    @GetMapping("/data")
    public String dataPelangganPage(Model model) {
        if (!hasPermission("READ")) {
            return "redirect:/dashboard?access_denied";
        }
        
        List<Customer> customers = customerService.getAllCustomers();
        model.addAttribute("customers", customers);
        model.addAttribute("permissionLevel", getCurrentUserPermissionLevel());
        model.addAttribute("page", "data-pelanggan");
        return "layout/base";
    }

    @PostMapping("/registrasi")
    public String registerCustomer(@ModelAttribute CustomerRegistrationDTO dto, Model model) {
        if (!hasPermission("CREATE")) {
            return "redirect:/dashboard?access_denied";
        }
        
        try {
            Customer customer = customerService.registerCustomer(dto);
            model.addAttribute("success", "Pelanggan berhasil didaftarkan! Kode: " + customer.getCustomerCode());
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        
        // Re-load data for dropdowns
        model.addAttribute("regions", customerService.getAllRegions());
        model.addAttribute("servers", customerService.getAllServers());
        model.addAttribute("odcs", customerService.getAllOdcs());
        model.addAttribute("odps", customerService.getAllOdps());
        model.addAttribute("packages", customerService.getAllPackages());
        model.addAttribute("serviceTypes", customerService.getAllServiceTypes());
        model.addAttribute("technicians", customerService.getAllTechnicians());
        model.addAttribute("permissionLevel", getCurrentUserPermissionLevel());
        
        return "pelanggan/registrasi";
    }

    // ==================== REST API ====================

@GetMapping("/api/regions")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<CompanyProfile>>> getCompanies() {
        List<CompanyProfile> companies = customerService.getAllCompanies();
        return ResponseEntity.ok(ApiResponse.success("Data company berhasil diambil", companies));
    }



    @GetMapping("/api/servers")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Server>>> getServers() {
        List<Server> servers = customerService.getAllServers();
        return ResponseEntity.ok(ApiResponse.success("Data server berhasil diambil", servers));
    }

    @GetMapping("/api/odcs")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<OdcOptionView>>> getOdcs() {
        List<OdcOptionView> odcs = customerService.getAllOdcs().stream()
                .map(this::toOdcOptionView)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Data ODC berhasil diambil", odcs));
    }

    @GetMapping("/api/odcs/{serverId}")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<OdcOptionView>>> getOdcsByServer(@PathVariable Long serverId) {
        List<OdcOptionView> odcs = customerService.getAllOdcs().stream()
                .filter(odc -> odc.getServer() != null && odc.getServer().getId().equals(serverId))
                .map(this::toOdcOptionView)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Data ODC berhasil diambil", odcs));
    }

@GetMapping("/api/odps")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<OdpOptionView>>> getOdps(@RequestParam(required = false) String type) {
        List<Odp> odps;
        if (type != null && !type.isEmpty()) {
            odps = customerService.getOdpsByType(type);
            return ResponseEntity.ok(ApiResponse.success(
                    "Data " + type.toUpperCase() + " berhasil diambil",
                    odps.stream().map(this::toOdpOptionView).toList()
            ));
        } else {
            odps = customerService.getAllOdps();
            return ResponseEntity.ok(ApiResponse.success(
                    "Data ODP berhasil diambil",
                    odps.stream().map(this::toOdpOptionView).toList()
            ));
        }
    }

    @GetMapping("/api/odps/available")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Odp>>> getAvailableOdps() {
        List<Odp> odps = customerService.getAvailableOdps();
        return ResponseEntity.ok(ApiResponse.success("Data ODP tersedia berhasil diambil", odps));
    }

    @GetMapping("/api/odps/{odcId}")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<OdpOptionView>>> getOdpsByOdc(@PathVariable Long odcId) {
        List<OdpOptionView> odps = customerService.getAllOdps().stream()
                .filter(odp -> odp.getOdc() != null && odp.getOdc().getId().equals(odcId))
                .map(this::toOdpOptionView)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Data ODP berhasil diambil", odps));
    }

    @GetMapping("/api/odps/{id}/availability")
    @ResponseBody
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOdpAvailability(@PathVariable Long id) {
        Odp odp = customerService.getOdpAvailability(id);
        Map<String, Object> availability = new HashMap<>();
        availability.put("id", odp.getId());
        availability.put("name", odp.getName());
        availability.put("code", odp.getCode());
        availability.put("capacity", odp.getCapacity());
        availability.put("usedPort", odp.getUsedPort());
        availability.put("availablePort", odp.getCapacity() - odp.getUsedPort());
        availability.put("canAdd", (odp.getCapacity() - odp.getUsedPort()) > 0);
        
        return ResponseEntity.ok(ApiResponse.success("Ketersediaan ODP", availability));
    }

    @GetMapping("/api/packages")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<InternetPackage>>> getPackages(@RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        List<InternetPackage> packages = includeInactive
                ? customerService.getAllPackagesIncludingInactive()
                : customerService.getAllPackages();
        return ResponseEntity.ok(ApiResponse.success("Data paket berhasil diambil", packages));
    }

    @GetMapping("/api/service-types")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<ServiceType>>> getServiceTypes(@RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        List<ServiceType> serviceTypes = includeInactive
                ? customerService.getAllServiceTypesIncludingInactive()
                : customerService.getAllServiceTypes();
        return ResponseEntity.ok(ApiResponse.success("Data tipe layanan berhasil diambil", serviceTypes));
    }

    // ================= PACKAGE CRUD APIs =================
    
    // Create Package
    @PostMapping("/api/packages")
    @ResponseBody
    public ResponseEntity<ApiResponse<InternetPackage>> createPackage(@RequestBody InternetPackage pkg) {
        if (!hasPermission("CREATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        
        try {
            InternetPackage saved = customerService.createPackage(pkg);
            return ResponseEntity.ok(ApiResponse.success("Paket berhasil dibuat!", saved));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    // Update Package
    @PutMapping("/api/packages/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<InternetPackage>> updatePackage(@PathVariable Long id, @RequestBody InternetPackage pkg) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Hanya Super Admin dan Admin yang dapat mengubah data"));
        }
        
        try {
            InternetPackage updated = customerService.updatePackage(id, pkg);
            return ResponseEntity.ok(ApiResponse.success("Paket berhasil diperbarui!", updated));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    // Delete Package
    @DeleteMapping("/api/packages/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> deletePackage(@PathVariable Long id) {
        if (!hasPermission("DELETE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Hanya Super Admin dan Admin yang dapat menghapus data"));
        }
        
        try {
            customerService.deletePackage(id);
            return ResponseEntity.ok(ApiResponse.success("Paket berhasil dihapus!", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    // ================= SERVICE TYPE CRUD APIs =================
    
    // Create Service Type
    @PostMapping("/api/service-types")
    @ResponseBody
    public ResponseEntity<ApiResponse<ServiceType>> createServiceType(@RequestBody ServiceType serviceType) {
        if (!hasPermission("CREATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        
        try {
            ServiceType saved = customerService.createServiceType(serviceType);
            return ResponseEntity.ok(ApiResponse.success("Tipe layanan berhasil dibuat!", saved));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    // Update Service Type
    @PutMapping("/api/service-types/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<ServiceType>> updateServiceType(@PathVariable Long id, @RequestBody ServiceType serviceType) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Hanya Super Admin dan Admin yang dapat mengubah data"));
        }
        
        try {
            ServiceType updated = customerService.updateServiceType(id, serviceType);
            return ResponseEntity.ok(ApiResponse.success("Tipe layanan berhasil diperbarui!", updated));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    // Delete Service Type
    @DeleteMapping("/api/service-types/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> deleteServiceType(@PathVariable Long id) {
        if (!hasPermission("DELETE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Hanya Super Admin dan Admin yang dapat menghapus data"));
        }
        
        try {
            customerService.deleteServiceType(id);
            return ResponseEntity.ok(ApiResponse.success("Tipe layanan berhasil dihapus!", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/technicians")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Technician>>> getTechnicians() {
        List<Technician> technicians = customerService.getAllTechnicians();
        return ResponseEntity.ok(ApiResponse.success("Data teknisi berhasil diambil", technicians));
    }

    // Customer Registration API - CREATE (All users)
    @PostMapping("/api/register")
    @ResponseBody
    public ResponseEntity<ApiResponse<Customer>> registerCustomerApi(@Valid @RequestBody CustomerRegistrationDTO dto) {
        if (!hasPermission("CREATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Anda tidak memiliki izin untuk menambah data"));
        }
        
        try {
            Customer customer = customerService.registerCustomer(dto);
            return ResponseEntity.ok(ApiResponse.success("Pelanggan berhasil didaftarkan!", customer));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Get Customer by ID - READ (All users)
    @GetMapping("/api/customers/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Customer>> getCustomer(@PathVariable Long id) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        
        try {
            Customer customer = customerService.getCustomerById(id);
            return ResponseEntity.ok(ApiResponse.success("Data pelanggan ditemukan", customer));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Get Customer by Code - READ (All users)
    @GetMapping("/api/customers/code/{customerCode}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Customer>> getCustomerByCode(@PathVariable String customerCode) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        
        try {
            Customer customer = customerService.getCustomerByCode(customerCode);
            return ResponseEntity.ok(ApiResponse.success("Data pelanggan ditemukan", customer));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Get All Customers - READ (All users)
    @GetMapping("/api/customers")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Customer>>> getAllCustomers() {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        
        List<Customer> customers = customerService.getAllCustomers();
        return ResponseEntity.ok(ApiResponse.success("Data pelanggan berhasil diambil", customers));
    }

    @GetMapping("/api/customers/{id}/detail")
    @ResponseBody
    public ResponseEntity<ApiResponse<CustomerEditDetailDTO>> getCustomerDetail(@PathVariable Long id) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }

        try {
            CustomerEditDetailDTO detail = customerService.getCustomerEditDetail(id);
            return ResponseEntity.ok(ApiResponse.success("Data pelanggan ditemukan", detail));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/customers/summary")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<CustomerDataRowDTO>>> getCustomerSummaries() {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }

        List<CustomerDataRowDTO> customers = customerService.getCustomerDataRows();
        return ResponseEntity.ok(ApiResponse.success("Ringkasan data pelanggan berhasil diambil", customers));
    }

    // Search Customers - READ (All users)
    @GetMapping("/api/customers/search")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Customer>>> searchCustomers(@RequestParam String keyword) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        
        List<Customer> customers = customerService.searchCustomers(keyword);
        return ResponseEntity.ok(ApiResponse.success("Hasil pencarian", customers));
    }

    // Update Customer - UPDATE (Super Admin & Admin only)
    @PutMapping("/api/customers/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Customer>> updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerRegistrationDTO dto) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Hanya Super Admin dan Admin yang dapat mengubah data"));
        }
        
        try {
            Customer customer = customerService.updateCustomer(id, dto);
            return ResponseEntity.ok(ApiResponse.success("Pelanggan berhasil diperbarui!", customer));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/api/customers/{id}/basic")
    @ResponseBody
    public ResponseEntity<ApiResponse<Customer>> updateCustomerBasic(@PathVariable Long id, @Valid @RequestBody CustomerBasicUpdateDTO dto) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Hanya Super Admin dan Admin yang dapat mengubah data"));
        }

        try {
            Customer customer = customerService.updateCustomerBasic(id, dto);
            return ResponseEntity.ok(ApiResponse.success("Pelanggan berhasil diperbarui!", customer));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Delete Customer - DELETE (Super Admin & Admin only)
    @DeleteMapping("/api/customers/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable Long id) {
        if (!hasPermission("DELETE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Hanya Super Admin dan Admin yang dapat menghapus data"));
        }
        
        try {
            customerService.deleteCustomer(id);
            return ResponseEntity.ok(ApiResponse.success("Pelanggan berhasil dihapus!", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Activate Customer Service - UPDATE (Super Admin & Admin only)
    @PostMapping("/api/services/{id}/activate")
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> activateService(@PathVariable Long id) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Hanya Super Admin dan Admin yang dapat mengaktivasi layanan"));
        }
        
        try {
            customerService.activateCustomerService(id);
            return ResponseEntity.ok(ApiResponse.success("Layanan berhasil diaktivasi!", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/api/customers/{id}/verify")
    @ResponseBody
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> verifyCustomer(
            @PathVariable Long id,
            @RequestBody(required = false) @Valid CustomerActivationRequest request
    ) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Hanya Super Admin dan Admin yang dapat memverifikasi pelanggan"));
        }

        try {
            Invoice invoice = customerService.verifyCustomerActivation(id, request);
            return ResponseEntity.ok(ApiResponse.success(
                    "Pelanggan berhasil diaktifkan dan pembayaran tercatat!",
                    customerService.getInvoiceRowById(invoice.getId())
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Suspend Customer Service - UPDATE (Super Admin & Admin only)
    @PostMapping("/api/services/{id}/suspend")
    @ResponseBody
    public ResponseEntity<ApiResponse<Void>> suspendService(@PathVariable Long id) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Hanya Super Admin dan Admin yang dapat menangguhkan layanan"));
        }
        
        try {
            customerService.suspendCustomerService(id);
            return ResponseEntity.ok(ApiResponse.success("Layanan berhasil ditangguhkan!", null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== INVOICE APIs ====================

    // Create Invoice
    @PostMapping("/api/invoices")
    @ResponseBody
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> createInvoice(@Valid @RequestBody InvoiceDTO dto) {
        if (!hasPermission("CREATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        
        try {
            Invoice invoice = customerService.createInvoice(dto);
            return ResponseEntity.ok(ApiResponse.success("Invoice berhasil dibuat!", customerService.getInvoiceRowById(invoice.getId())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Get Invoice by ID
    @GetMapping("/api/invoices/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> getInvoice(@PathVariable Long id) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        
        try {
            return ResponseEntity.ok(ApiResponse.success("Data invoice ditemukan", customerService.getInvoiceRowById(id)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/invoices")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<InvoiceRowDTO>>> getAllInvoiceRows(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String dueDay,
            @RequestParam(required = false) String status) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }

        try {
            Integer selectedDueDay = parseDueDay(dueDay);
            List<InvoiceRowDTO> invoices = customerService.getAllInvoiceRows().stream()
                    .filter(invoice -> invoiceMatchesDate(invoice, selectedDueDay, year, month))
                    .filter(invoice -> invoiceMatchesStatus(invoice, status))
                    .toList();
            return ResponseEntity.ok(ApiResponse.success("Data invoice berhasil diambil", invoices));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/invoices/years")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Integer>>> getInvoiceYears() {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }

        try {
            return ResponseEntity.ok(ApiResponse.success("Data tahun invoice berhasil diambil", customerService.getDistinctInvoiceYears()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/api/invoices/months")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Integer>>> getInvoiceMonthsByYear(@RequestParam Integer year) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }

        try {
            return ResponseEntity.ok(ApiResponse.success("Data bulan invoice berhasil diambil", customerService.getDistinctInvoiceMonthsByYear(year)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Get Invoices by Customer
    @GetMapping("/api/customers/{customerId}/invoices")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<InvoiceRowDTO>>> getCustomerInvoices(@PathVariable Long customerId) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        
        try {
            List<InvoiceRowDTO> invoices = customerService.getInvoiceRowsByCustomer(customerId);
            return ResponseEntity.ok(ApiResponse.success("Data invoice berhasil diambil", invoices));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Get Unpaid Invoices (for current month billing)
    @GetMapping("/api/invoices/unpaid")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<InvoiceRowDTO>>> getUnpaidInvoices() {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        
        try {
            List<InvoiceRowDTO> unpaidInvoices = customerService.getUnpaidInvoiceRows();
            return ResponseEntity.ok(ApiResponse.success("Data invoice unpaid berhasil diambil", unpaidInvoices));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Pay Invoice
    @PostMapping("/api/invoices/{id}/pay")
    @ResponseBody
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> payInvoice(@PathVariable Long id, @RequestBody Map<String, Object> paymentData) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Hanya Super Admin dan Admin yang dapat melakukan pembayaran"));
        }
        
        try {
            BigDecimal amount = new BigDecimal(paymentData.get("amount").toString());
            String paymentMethod = (String) paymentData.get("paymentMethod");
            String notes = (String) paymentData.getOrDefault("notes", "");
            
            Invoice invoice = customerService.payInvoice(id, amount, paymentMethod, notes);
            return ResponseEntity.ok(ApiResponse.success("Pembayaran berhasil!", customerService.getInvoiceRowById(invoice.getId())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Cancel Invoice
    @PostMapping("/api/invoices/{id}/cancel")
    @ResponseBody
    public ResponseEntity<ApiResponse<InvoiceRowDTO>> cancelInvoice(@PathVariable Long id) {
        if (!hasPermission("UPDATE")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak - Hanya Super Admin dan Admin yang dapat membatalkan invoice"));
        }
        
        try {
            Invoice invoice = customerService.cancelInvoice(id);
            return ResponseEntity.ok(ApiResponse.success("Invoice berhasil dibatalkan!", customerService.getInvoiceRowById(invoice.getId())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    // ==================== HISTORY PAYMENT APIs ====================
    
    // Get Invoice by Customer with Year and Month filter
    @GetMapping("/api/customers/{customerId}/invoices/filter")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<InvoiceRowDTO>>> getCustomerInvoicesFiltered(
            @PathVariable Long customerId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        
        try {
            List<InvoiceRowDTO> invoices;
            if (year != null && month != null) {
                invoices = customerService.getInvoiceRowsByCustomerYearAndMonth(customerId, year, month);
            } else if (year != null) {
                invoices = customerService.getInvoiceRowsByCustomerYear(customerId, year);
            } else {
                invoices = customerService.getInvoiceRowsByCustomer(customerId);
            }
            return ResponseEntity.ok(ApiResponse.success("Data invoice berhasil diambil", invoices));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    // Get available years for a customer
    @GetMapping("/api/customers/{customerId}/invoices/years")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Integer>>> getCustomerInvoiceYears(@PathVariable Long customerId) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        
        try {
            List<Integer> years = customerService.getDistinctYearsByCustomer(customerId);
            return ResponseEntity.ok(ApiResponse.success("Data tahun berhasil diambil", years));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    // Get available months for a customer and year
    @GetMapping("/api/customers/{customerId}/invoices/months")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<Integer>>> getCustomerInvoiceMonths(
            @PathVariable Long customerId,
            @RequestParam Integer year) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        
        try {
            List<Integer> months = customerService.getDistinctMonthsByCustomerAndYear(customerId, year);
            return ResponseEntity.ok(ApiResponse.success("Data bulan berhasil diambil", months));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
    
    // Get paid invoices for a customer
    @GetMapping("/api/customers/{customerId}/invoices/paid")
    @ResponseBody
    public ResponseEntity<ApiResponse<List<InvoiceRowDTO>>> getPaidInvoices(@PathVariable Long customerId) {
        if (!hasPermission("READ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Akses ditolak"));
        }
        
        try {
            List<InvoiceRowDTO> invoices = customerService.getPaidInvoiceRowsByCustomer(customerId);
            return ResponseEntity.ok(ApiResponse.success("Data invoice lunas berhasil diambil", invoices));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    private OdcOptionView toOdcOptionView(Odc odc) {
        Server server = odc.getServer();
        return OdcOptionView.builder()
                .id(odc.getId())
                .name(odc.getName())
                .code(odc.getCode())
                .location(odc.getLocation())
                .serverId(server != null ? server.getId() : null)
                .serverName(server != null ? server.getName() : null)
                .capacity(odc.getCapacity())
                .usedPort(odc.getUsedPort())
                .build();
    }

    private OdpOptionView toOdpOptionView(Odp odp) {
        Odc odc = odp.getOdc();
        Server server = odc != null ? odc.getServer() : null;
        CompanyProfile companyProfile = odp.getCompanyProfile();
        return OdpOptionView.builder()
                .id(odp.getId())
                .name(odp.getName())
                .code(odp.getCode())
                .nodeType(odp.getNodeType())
                .location(odp.getLocation())
                .splitter(odp.getSplitter())
                .capacity(odp.getCapacity())
                .usedPort(odp.getUsedPort())
                .odcId(odc != null ? odc.getId() : null)
                .odcName(odc != null ? odc.getName() : null)
                .serverId(server != null ? server.getId() : null)
                .serverName(server != null ? server.getName() : null)
                .companyProfileId(companyProfile != null ? companyProfile.getId() : null)
                .companyProfileName(companyProfile != null ? companyProfile.getName() : null)
                .build();
    }

    private boolean invoiceMatchesPeriod(InvoiceRowDTO invoice, Integer year, Integer month) {
        if (invoice == null) {
            return false;
        }
        if (year == null && month == null) {
            return true;
        }

        LocalDate referenceDate = invoice.getBillingMonth() != null ? invoice.getBillingMonth() : invoice.getDueDate();
        if (referenceDate == null) {
            return false;
        }
        if (year != null && referenceDate.getYear() != year) {
            return false;
        }
        return month == null || referenceDate.getMonthValue() == month;
    }

    private boolean invoiceMatchesDate(InvoiceRowDTO invoice, Integer dueDay, Integer year, Integer month) {
        if (invoice == null) {
            return false;
        }
        if (!invoiceMatchesPeriod(invoice, year, month)) {
            return false;
        }
        if (dueDay == null) {
            return true;
        }
        LocalDate dueDate = invoice.getDueDate();
        return dueDate != null && dueDate.getDayOfMonth() == dueDay;
    }

    private Integer parseDueDay(String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value)) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= 1 && parsed <= 31 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean invoiceMatchesStatus(InvoiceRowDTO invoice, String status) {
        if (invoice == null) {
            return false;
        }
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return true;
        }

        String invoiceStatus = invoice.getStatus() != null ? invoice.getStatus().toLowerCase(Locale.ROOT) : "";
        String normalizedStatus = status.toLowerCase(Locale.ROOT);
        if ("unpaid".equals(normalizedStatus) || "belum-bayar".equals(normalizedStatus)) {
            return "pending".equals(invoiceStatus) || "partial".equals(invoiceStatus);
        }
        return normalizedStatus.equals(invoiceStatus);
    }

}

