package com.netmaster.nmx.service;

import com.netmaster.nmx.dto.CustomerEditDetailDTO;
import com.netmaster.nmx.dto.CustomerRegistrationDTO;
import com.netmaster.nmx.dto.CustomerBasicUpdateDTO;
import com.netmaster.nmx.dto.CustomerDataRowDTO;
import com.netmaster.nmx.dto.CustomerActivationRequest;
import com.netmaster.nmx.dto.HistoryPaymentRowDTO;
import com.netmaster.nmx.dto.InvoiceDTO;
import com.netmaster.nmx.dto.InvoiceRowDTO;
import com.netmaster.nmx.dto.TechnicianRequest;
import com.netmaster.nmx.dto.TechnicianView;
import com.netmaster.nmx.model.*;
import java.math.BigDecimal;
import java.util.List;

public interface ICustomerService {
    
    // Customer operations
    Customer registerCustomer(CustomerRegistrationDTO dto);
    Customer updateCustomer(Long id, CustomerRegistrationDTO dto);
    Customer getCustomerById(Long id);
    Customer getCustomerByCode(String customerCode);
    CustomerEditDetailDTO getCustomerEditDetail(Long id);
    List<Customer> getAllCustomers();
    List<CustomerDataRowDTO> getCustomerDataRows();
    List<HistoryPaymentRowDTO> getHistoryPaymentRowsByStatus(String statusFilter);
    List<Customer> searchCustomers(String keyword);
    void deleteCustomer(Long id);
    Customer updateCustomerBasic(Long id, CustomerBasicUpdateDTO dto);
    
    // Customer Service operations
    CustomerServiceEntity createCustomerService(Long customerId, CustomerRegistrationDTO dto);
    CustomerServiceEntity activateCustomerService(Long customerServiceId);
    CustomerServiceEntity suspendCustomerService(Long customerServiceId);
    Invoice verifyCustomerActivation(Long customerId, CustomerActivationRequest request);
    
    // Invoice operations
    Invoice createInvoice(InvoiceDTO dto);
    Invoice updateInvoice(Long id, InvoiceDTO dto);
    Invoice getInvoiceById(Long id);
    InvoiceRowDTO getInvoiceRowById(Long id);
    List<Invoice> getAllInvoices();
    List<InvoiceRowDTO> getAllInvoiceRows();
    List<InvoiceRowDTO> getInvoiceRowsByCustomer(Long customerId);
    List<InvoiceRowDTO> getUnpaidInvoiceRows();
    List<InvoiceRowDTO> getInvoiceRowsByCustomerYearAndMonth(Long customerId, Integer year, Integer month);
    List<InvoiceRowDTO> getInvoiceRowsByCustomerYear(Long customerId, Integer year);
    List<InvoiceRowDTO> getPaidInvoiceRowsByCustomer(Long customerId);
    List<Invoice> getInvoicesByCustomer(Long customerId);
    List<Invoice> getInvoicesByCustomerAndStatus(Long customerId, String status);
    List<Invoice> getInvoicesByStatus(String status);
    List<Invoice> getUnpaidInvoices();
    List<Invoice> getInvoicesByCustomerYearAndMonth(Long customerId, Integer year, Integer month);
    List<Invoice> getInvoicesByCustomerYear(Long customerId, Integer year);
    List<Invoice> getPaidInvoicesByCustomer(Long customerId);
    List<Invoice> getPaidInvoices();
    List<Integer> getDistinctInvoiceYears();
    List<Integer> getDistinctInvoiceMonthsByYear(Integer year);
    List<Integer> getDistinctYearsByCustomer(Long customerId);
    List<Integer> getDistinctMonthsByCustomerAndYear(Long customerId, Integer year);
    Invoice payInvoice(Long invoiceId, BigDecimal amount, String paymentMethod, String notes);
    Invoice cancelInvoice(Long invoiceId);
    void deleteInvoice(Long invoiceId);
    // Lookup operations
    List<Region> getAllRegions();
    List<Server> getAllServers();
    List<Odc> getAllOdcs();
    List<Odp> getAllOdps();
    List<Odp> getAvailableOdps();

    // Packages & service types
    // getAllPackages()/getAllServiceTypes() are used by customer-facing dropdowns (active only)
    List<InternetPackage> getAllPackages();
    List<InternetPackage> getAllPackagesIncludingInactive();
    List<ServiceType> getAllServiceTypes();
    List<ServiceType> getAllServiceTypesIncludingInactive();
    // NEW: for registrasi enhancements
    List<CompanyProfile> getAllCompanies();
    List<Odp> getOdpsByType(String nodeType);
    List<Technician> getAllTechnicians();
    List<TechnicianView> getAllTechnicianViews();
    TechnicianView createTechnician(TechnicianRequest request);
    TechnicianView updateTechnician(Long id, TechnicianRequest request);
    void deleteTechnician(Long id);

    // ODP availability
    Odp getOdpAvailability(Long odpId);
    
    // Package CRUD
    InternetPackage createPackage(InternetPackage pkg);
    InternetPackage updatePackage(Long id, InternetPackage pkg);
    void deletePackage(Long id);
    
    // Service Type CRUD
    ServiceType createServiceType(ServiceType serviceType);
    ServiceType updateServiceType(Long id, ServiceType serviceType);
    void deleteServiceType(Long id);
}
