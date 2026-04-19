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
import com.netmaster.nmx.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceImpl implements ICustomerService {

    private static final long PAYMENT_REMINDER_WINDOW_DAYS = 3L;

    private static final Map<String, String> REGISTRATION_SERVICE_TYPES = new LinkedHashMap<>();

    static {
        REGISTRATION_SERVICE_TYPES.put("Dedicated BW", "Dedicated Bandwidth - Fixed speed");
        REGISTRATION_SERVICE_TYPES.put("Up To BW", "Up To Bandwidth - Shared speed");
        REGISTRATION_SERVICE_TYPES.put("Broadcast", "Broadcast service for multicast");
        REGISTRATION_SERVICE_TYPES.put("Metro", "Metro Ethernet service");
    }

    private final CustomerRepository customerRepository;
    private final CustomerServiceEntityRepository customerServiceEntityRepository;
    private final RegionRepository regionRepository;
    private final ServerRepository serverRepository;
    private final OdcRepository odcRepository;
    private final OdpRepository odpRepository;
    private final InternetPackageRepository internetPackageRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final TechnicianRepository technicianRepository;
    private final OltDeviceRepository oltDeviceRepository;
    private final InvoiceRepository invoiceRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final TicketRepository ticketRepository;
    private final PaymentManagementService paymentManagementService;
    private final BillingInvoiceService billingInvoiceService;
    private final BillingQuickActionService billingQuickActionService;
    private final InvoiceNumberService invoiceNumberService;

    @Override
    @Transactional
    public Customer registerCustomer(CustomerRegistrationDTO dto) {
        // Check if ktpNumber already exists
        if (StringUtils.hasText(dto.getKtpNumber())
                && customerRepository.findByKtpNumber(dto.getKtpNumber()).isPresent()) {
            throw new RuntimeException("Nomor KTP sudah terdaftar!");
        }

        // Check if phone already exists
        if (customerRepository.findByPhone(dto.getPhone()).isPresent()) {
            throw new RuntimeException("Nomor telepon sudah terdaftar!");
        }

        // Generate customer code
        String customerCode = generateCustomerCode();

        // Create customer
        Customer customer = new Customer();
        customer.setCustomerCode(customerCode);
        customer.setFullName(dto.getFullName());
        customer.setEmail(trimToNull(dto.getEmail()));
        customer.setPhone(dto.getPhone());
        customer.setKtpNumber(trimToNull(dto.getKtpNumber()));
        customer.setKtpAddress(trimToNull(dto.getKtpAddress()));
        customer.setInstallationAddress(dto.getInstallationAddress());
        customer.setStatus("pending");
        customer.setRegistrationDate(LocalDate.now());

        // Set region
        if (dto.getRegionId() != null) {
            Region region = regionRepository.findById(dto.getRegionId())
                    .orElseThrow(() -> new RuntimeException("Region tidak ditemukan!"));
            customer.setRegion(region);
        } else if (dto.getServerId() != null) {
            Server server = serverRepository.findById(dto.getServerId())
                    .orElseThrow(() -> new RuntimeException("Server/OLT tidak ditemukan!"));
            customer.setRegion(server.getRegion());
        }

        // Set coordinates
        customer.setLatitude(parseDecimalOrNull(dto.getLatitude()));
        customer.setLongitude(parseDecimalOrNull(dto.getLongitude()));

        customer.setNotes(dto.getNotes());

        // Save customer
        customer = customerRepository.save(customer);

        // Create customer service
        createCustomerService(customer.getId(), dto);

        return customer;
    }

    @Override
    @Transactional
    public Customer updateCustomer(Long id, CustomerRegistrationDTO dto) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pelanggan tidak ditemukan!"));

        validateCustomerUniqueness(dto, id);

        customer.setFullName(dto.getFullName());
        customer.setEmail(trimToNull(dto.getEmail()));
        customer.setPhone(dto.getPhone());
        customer.setKtpNumber(trimToNull(dto.getKtpNumber()));
        customer.setKtpAddress(trimToNull(dto.getKtpAddress()));
        customer.setInstallationAddress(dto.getInstallationAddress());

        if (dto.getRegionId() != null) {
            Region region = regionRepository.findById(dto.getRegionId())
                    .orElseThrow(() -> new RuntimeException("Region tidak ditemukan!"));
            customer.setRegion(region);
        } else if (dto.getServerId() != null) {
            Server server = serverRepository.findById(dto.getServerId())
                    .orElseThrow(() -> new RuntimeException("Server/OLT tidak ditemukan!"));
            customer.setRegion(server.getRegion());
        } else {
            customer.setRegion(null);
        }

        customer.setLatitude(parseDecimalOrNull(dto.getLatitude()));
        customer.setLongitude(parseDecimalOrNull(dto.getLongitude()));

        customer.setNotes(dto.getNotes());

        Customer savedCustomer = customerRepository.save(customer);
        upsertCustomerService(savedCustomer, dto);
        return savedCustomer;
    }

    @Override
    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pelanggan tidak ditemukan!"));
    }

    @Override
    public Customer getCustomerByCode(String customerCode) {
        return customerRepository.findByCustomerCode(customerCode)
                .orElseThrow(() -> new RuntimeException("Pelanggan tidak ditemukan!"));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerEditDetailDTO getCustomerEditDetail(Long id) {
        Customer customer = getCustomerById(id);
        CustomerServiceEntity service = customerServiceEntityRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(id)
                .orElse(null);

        Odp odp = service != null ? service.getOdp() : null;
        Odc odc = odp != null ? odp.getOdc() : null;
        Server server = odc != null ? odc.getServer() : null;
        CompanyProfile companyProfile = odp != null ? odp.getCompanyProfile() : null;

        return CustomerEditDetailDTO.builder()
                .id(customer.getId())
                .customerCode(customer.getCustomerCode())
                .fullName(customer.getFullName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .ktpNumber(customer.getKtpNumber())
                .ktpAddress(customer.getKtpAddress())
                .installationAddress(customer.getInstallationAddress())
                .regionId(customer.getRegion() != null ? customer.getRegion().getId() : null)
                .companyProfileId(companyProfile != null ? companyProfile.getId() : null)
                .serverId(server != null ? server.getId() : null)
                .odcId(odc != null ? odc.getId() : null)
                .odpId(odp != null ? odp.getId() : null)
                .packageId(service != null && service.getInternetPackage() != null ? service.getInternetPackage().getId() : null)
                .packageName(service != null && service.getInternetPackage() != null ? service.getInternetPackage().getName() : null)
                .monthlyFee(service != null ? service.getMonthlyFee() : BigDecimal.ZERO)
                .odpPort(service != null ? service.getOdpPort() : null)
                .latitude(customer.getLatitude() != null ? customer.getLatitude().toPlainString() : null)
                .longitude(customer.getLongitude() != null ? customer.getLongitude().toPlainString() : null)
                .build();
    }

    @Override
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    @Override
    @Transactional
    public List<CustomerDataRowDTO> getCustomerDataRows() {
        LocalDate today = LocalDate.now();
        return customerRepository.findAll().stream()
                .map(customer -> {
                    CustomerServiceEntity service = customerServiceEntityRepository
                            .findTopWithInternetPackageByCustomerIdOrderByCreatedAtDesc(customer.getId())
                            .orElse(null);

                    List<Invoice> invoices = synchronizeInvoiceStatuses(
                            invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customer.getId())
                    );
                    String paymentStatus = resolveCustomerPaymentStatus(customer, service, invoices, today);

                    return new CustomerDataRowDTO(
                            customer.getId(),
                            customer.getCustomerCode(),
                            customer.getFullName(),
                            customer.getPhone(),
                            customer.getEmail(),
                            customer.getKtpNumber(),
                            customer.getInstallationAddress(),
                            normalizeCustomerStatus(customer.getStatus()),
                            customer.getRegistrationDate(),
                            service != null ? service.getId() : null,
                            service != null ? service.getPppoeUsername() : null,
                            service != null && service.getInternetPackage() != null ? service.getInternetPackage().getName() : null,
                            service != null ? service.getIpAddress() : null,
                            service != null ? service.getMonthlyFee() : BigDecimal.ZERO,
                            service != null ? defaultAmount(service.getInstallationFee()) : BigDecimal.ZERO,
                            paymentStatus
                    );
                })
                .toList();
    }

    @Override
    @Transactional
    public List<HistoryPaymentRowDTO> getHistoryPaymentRowsByStatus(String statusFilter) {
        return paymentManagementService.getCustomerHistoryRows(statusFilter);
    }

    @Override
    public List<Customer> searchCustomers(String keyword) {
        return customerRepository.searchCustomers(keyword, keyword, keyword);
    }

    @Override
    @Transactional
    public Customer updateCustomerBasic(Long id, CustomerBasicUpdateDTO dto) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pelanggan tidak ditemukan!"));

        customerRepository.findByPhone(dto.getPhone())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new RuntimeException("Nomor telepon sudah terdaftar!");
                });

        if (StringUtils.hasText(dto.getEmail())) {
            customerRepository.findByEmail(dto.getEmail())
                    .filter(existing -> !existing.getId().equals(id))
                    .ifPresent(existing -> {
                        throw new RuntimeException("Email sudah terdaftar!");
                    });
        }

        if (dto.getKtpNumber() != null) {
            String ktpNumber = trimToNull(dto.getKtpNumber());
            if (StringUtils.hasText(ktpNumber)) {
                customerRepository.findByKtpNumber(ktpNumber)
                        .filter(existing -> !existing.getId().equals(id))
                        .ifPresent(existing -> {
                            throw new RuntimeException("Nomor KTP sudah terdaftar!");
                        });
            }
            customer.setKtpNumber(ktpNumber);
        }

        customer.setFullName(dto.getFullName().trim());
        customer.setPhone(dto.getPhone().trim());
        customer.setEmail(trimToNull(dto.getEmail()));
        customer.setInstallationAddress(dto.getInstallationAddress().trim());

        if (dto.getKtpAddress() != null) {
            customer.setKtpAddress(trimToNull(dto.getKtpAddress()));
        }

        customer.setLatitude(parseDecimalOrNull(dto.getLatitude()));
        customer.setLongitude(parseDecimalOrNull(dto.getLongitude()));

        Optional<CustomerServiceEntity> serviceOptional = customerServiceEntityRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId());

        if (serviceOptional.isPresent()) {
            CustomerServiceEntity service = serviceOptional.get();
            Odp currentOdp = service.getOdp();
            Integer currentPort = service.getOdpPort();
            Odp selectedOdp = resolveSelectedOdp(dto.getOdpId(), dto.getOdcId(), dto.getServerId(), dto.getCompanyProfileId());
            Integer selectedPort = selectedOdp != null ? dto.getOdpPort() : null;

            if (dto.getServerId() != null) {
                Server server = serverRepository.findById(dto.getServerId())
                        .orElseThrow(() -> new RuntimeException("Server/OLT tidak ditemukan!"));
                customer.setRegion(server.getRegion());
            } else if (selectedOdp != null && selectedOdp.getOdc() != null && selectedOdp.getOdc().getServer() != null) {
                customer.setRegion(selectedOdp.getOdc().getServer().getRegion());
            } else {
                customer.setRegion(null);
            }

            syncOdpAllocation(service, selectedOdp, selectedPort, currentOdp, currentPort);
            service.setOdp(selectedOdp);
            service.setOdpPort(selectedPort);

            if (dto.getPackageId() != null) {
                InternetPackage internetPackage = internetPackageRepository.findById(dto.getPackageId())
                        .orElseThrow(() -> new RuntimeException("Paket internet tidak ditemukan!"));
                service.setInternetPackage(internetPackage);
                service.setMonthlyFee(defaultAmount(internetPackage.getPrice()));
                synchronizeOutstandingInvoiceAmounts(customer, service);
            }

            customerServiceEntityRepository.save(service);
        } else if (dto.getOdpId() != null || dto.getOdpPort() != null || dto.getPackageId() != null) {
            throw new RuntimeException("Layanan pelanggan belum tersedia!");
        } else if (dto.getServerId() != null) {
            Server server = serverRepository.findById(dto.getServerId())
                    .orElseThrow(() -> new RuntimeException("Server/OLT tidak ditemukan!"));
            customer.setRegion(server.getRegion());
        } else {
            customer.setRegion(null);
        }

        return customerRepository.save(customer);
    }

    private void synchronizeOutstandingInvoiceAmounts(Customer customer, CustomerServiceEntity service) {
        BigDecimal monthlyFee = defaultAmount(service.getMonthlyFee());
        List<Invoice> invoices = invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customer.getId());
        for (Invoice invoice : invoices) {
            if (invoice == null || "paid".equalsIgnoreCase(invoice.getStatus()) || "cancelled".equalsIgnoreCase(invoice.getStatus())) {
                continue;
            }

            boolean activationInvoice = "activation".equalsIgnoreCase(invoice.getInvoiceType());
            BigDecimal normalizedMonthlyFee = activationInvoice ? BigDecimal.ZERO : monthlyFee;
            BigDecimal installationFee = defaultAmount(invoice.getInstallationFee());
            BigDecimal otherCharges = defaultAmount(invoice.getOtherCharges());

            invoice.setCustomerService(service);
            invoice.setMonthlyFee(normalizedMonthlyFee);
            invoice.setInstallationFee(installationFee);
            invoice.setOtherCharges(otherCharges);
            invoice.setTotalAmount(normalizedMonthlyFee
                    .add(installationFee)
                    .add(otherCharges));
            invoice.setStatus(computeInvoiceStatus(invoice));
            invoiceRepository.save(invoice);
        }
    }

    @Override
    @Transactional
    public void deleteCustomer(Long id) {
        billingQuickActionService.softDeleteCustomer(id);
    }

    @Override
    @Transactional
    public CustomerServiceEntity createCustomerService(Long customerId, CustomerRegistrationDTO dto) {
        Customer customer = getCustomerById(customerId);
        CustomerServiceEntity customerService = new CustomerServiceEntity();
        customerService.setCustomer(customer);
        return upsertCustomerService(customerService, dto);
    }

    @Override
    @Transactional
    public CustomerServiceEntity activateCustomerService(Long customerServiceId) {
        CustomerServiceEntity cs = customerServiceEntityRepository.findById(customerServiceId)
                .orElseThrow(() -> new RuntimeException("Layanan tidak ditemukan!"));

        Customer customer = cs.getCustomer();
        activateCustomerAndRecordPayment(customer, cs);
        return cs;
    }

    @Override
    @Transactional
    public CustomerServiceEntity suspendCustomerService(Long customerServiceId) {
        CustomerServiceEntity cs = customerServiceEntityRepository.findById(customerServiceId)
                .orElseThrow(() -> new RuntimeException("Layanan tidak ditemukan!"));

        cs.setStatus("suspended");

        // Update customer status
        Customer customer = cs.getCustomer();
        customer.setStatus("suspended");
        customerRepository.save(customer);

        return customerServiceEntityRepository.save(cs);
    }

    @Override
    @Transactional
    public Invoice verifyCustomerActivation(Long customerId, CustomerActivationRequest request) {
        Customer customer = getCustomerById(customerId);
        CustomerServiceEntity service = customerServiceEntityRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customerId)
                .orElseThrow(() -> new RuntimeException("Layanan pelanggan tidak ditemukan!"));
        ActivationOptions options = resolveActivationOptions(service, request);
        return activateCustomerAndRecordPayment(customer, service, options);
    }

    @Override
    public List<Region> getAllRegions() {
        return regionRepository.findAll();
    }

    @Override
    public List<Server> getAllServers() {
        syncServersFromActiveOlts();
        return serverRepository.findByIsActiveTrueOrderByNameAsc();
    }

    @Override
    public List<Odc> getAllOdcs() {
        return odcRepository.findAllActiveWithServer();
    }

    @Override
    public List<Odp> getAllOdps() {
        return odpRepository.findAllActiveWithOdcAndServer();
    }

    @Override
    public List<Odp> getAvailableOdps() {
        return odpRepository.findAvailableOdps();
    }

    @Override
    public List<InternetPackage> getAllPackages() {
        return internetPackageRepository.findByIsActiveTrueOrderByPriceAsc();
    }

    

    @Override
    public List<InternetPackage> getAllPackagesIncludingInactive() {
        return internetPackageRepository.findAllByOrderByPriceAsc();
    }

    @Override
    public List<ServiceType> getAllServiceTypes() {
        ensureRegistrationServiceTypes();
        return serviceTypeRepository.findByIsActiveTrueOrderByNameAsc();
    }

    

    @Override
    public List<ServiceType> getAllServiceTypesIncludingInactive() {
        ensureRegistrationServiceTypes();
        return serviceTypeRepository.findAllByOrderByNameAsc();
    }

    @Override
    public List<Technician> getAllTechnicians() {
        return technicianRepository.findByIsActiveTrueOrderByNameAsc();
    }

    @Override
    public List<TechnicianView> getAllTechnicianViews() {
        return technicianRepository.findAllByOrderByNameAsc().stream()
                .map(this::toTechnicianView)
                .toList();
    }

    @Override
    @Transactional
    public TechnicianView createTechnician(TechnicianRequest request) {
        Technician technician = new Technician();
        applyTechnicianRequest(technician, request);
        return toTechnicianView(technicianRepository.save(technician));
    }

    @Override
    @Transactional
    public TechnicianView updateTechnician(Long id, TechnicianRequest request) {
        Technician technician = technicianRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Teknisi tidak ditemukan!"));
        applyTechnicianRequest(technician, request);
        return toTechnicianView(technicianRepository.save(technician));
    }

    @Override
    @Transactional
    public void deleteTechnician(Long id) {
        Technician technician = technicianRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Teknisi tidak ditemukan!"));

        long ticketUsage = ticketRepository.countByAssignedTechnicianId(id);
        long serviceUsage = customerServiceEntityRepository.countByTechnicianId(id);
        if (ticketUsage > 0 || serviceUsage > 0) {
            throw new RuntimeException("Teknisi tidak dapat dihapus karena masih digunakan pada data ticket atau layanan pelanggan.");
        }

        technicianRepository.delete(technician);
    }

@Override
    public List<CompanyProfile> getAllCompanies() {
        return companyProfileRepository.findByIsActiveTrueOrderByIdAsc();
    }

    @Override
    public List<Odp> getOdpsByType(String nodeType) {
        return odpRepository.findByNodeTypeIgnoreCaseAndIsActiveTrueWithRelations(nodeType.toUpperCase());
    }

    @Override
    public Odp getOdpAvailability(Long odpId) {
        return odpRepository.findById(odpId)
                .orElseThrow(() -> new RuntimeException("ODP tidak ditemukan!"));
    }
    
    // ================= PACKAGE CRUD =================
    
    @Override
    @Transactional
    public InternetPackage createPackage(InternetPackage pkg) {
        if (pkg.getIsActive() == null) {
            pkg.setIsActive(true);
        }
        return internetPackageRepository.save(pkg);
    }
    
    @Override
    @Transactional
    public InternetPackage updatePackage(Long id, InternetPackage pkg) {
        InternetPackage existing = internetPackageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Paket tidak ditemukan!"));
        
        existing.setName(pkg.getName());
        existing.setSpeedDown(pkg.getSpeedDown());
        existing.setSpeedUp(pkg.getSpeedUp());
        existing.setBurstDownload(pkg.getBurstDownload());
        existing.setBurstUpload(pkg.getBurstUpload());
        existing.setMikrotikProfileName(pkg.getMikrotikProfileName());
        existing.setPrice(pkg.getPrice());
        existing.setDescription(pkg.getDescription());
        existing.setIsActive(pkg.getIsActive());
        
        return internetPackageRepository.save(existing);
    }
    
    @Override
    @Transactional
    public void deletePackage(Long id) {
        if (!internetPackageRepository.existsById(id)) {
            throw new RuntimeException("Paket tidak ditemukan!");
        }
        internetPackageRepository.deleteById(id);
    }
    
    // ================= SERVICE TYPE CRUD =================
    
    @Override
    @Transactional
    public ServiceType createServiceType(ServiceType serviceType) {
        if (serviceType.getIsActive() == null) {
            serviceType.setIsActive(true);
        }
        return serviceTypeRepository.save(serviceType);
    }
    
    @Override
    @Transactional
    public ServiceType updateServiceType(Long id, ServiceType serviceType) {
        ServiceType existing = serviceTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tipe layanan tidak ditemukan!"));
        
        existing.setName(serviceType.getName());
        existing.setDescription(serviceType.getDescription());
        
        return serviceTypeRepository.save(existing);
    }
    
    @Override
    @Transactional
    public void deleteServiceType(Long id) {
        if (!serviceTypeRepository.existsById(id)) {
            throw new RuntimeException("Tipe layanan tidak ditemukan!");
        }
        serviceTypeRepository.deleteById(id);
    }

    private String generateCustomerCode() {
        String prefix = "CSM";
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String searchPrefix = prefix + "-" + today + "-";

        Optional<Customer> latestCustomer = customerRepository.findTopByCustomerCodeStartingWithOrderByCustomerCodeDesc(searchPrefix);
        int nextSeq = latestCustomer
                .map(Customer::getCustomerCode)
                .map(code -> code.substring(code.lastIndexOf('-') + 1))
                .map(Integer::parseInt)
                .orElse(0) + 1;

        return prefix + "-" + today + "-" + String.format("%03d", nextSeq);
    }
    
    // ==================== INVOICE METHODS ====================
    
    @Override
    @Transactional
    public Invoice createInvoice(InvoiceDTO dto) {
        Customer customer = getCustomerById(dto.getCustomerId());
        CustomerServiceEntity customerService = resolveCustomerServiceForInvoice(customer, dto.getCustomerServiceId());

        LocalDate billingMonth = dto.getDueDate() != null ? dto.getDueDate() : dto.getBillingMonth();
        if (billingMonth == null) {
            billingMonth = resolveNextBillingAnchor(customer, customerService, null);
        }

        validateInvoiceUniqueness(dto.getCustomerId(), billingMonth, null);

        LocalDate dueDate = dto.getDueDate() != null
                ? dto.getDueDate()
                : resolveNextDueDate(customer, customerService, null, billingMonth);

        BigDecimal monthlyFee = dto.getMonthlyFee() != null
                ? dto.getMonthlyFee()
                : customerService != null ? defaultAmount(customerService.getMonthlyFee()) : BigDecimal.ZERO;
        BigDecimal installationFee = dto.getInstallationFee() != null ? dto.getInstallationFee() : BigDecimal.ZERO;
        BigDecimal otherCharges = dto.getOtherCharges() != null ? dto.getOtherCharges() : BigDecimal.ZERO;
        BigDecimal totalAmount = monthlyFee.add(installationFee).add(otherCharges);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invoiceNumberService.generate());
        invoice.setCustomer(customer);
        invoice.setCustomerService(customerService);
        invoice.setBillingMonth(billingMonth);
        invoice.setDueDate(dueDate);
        invoice.setMonthlyFee(monthlyFee);
        invoice.setInstallationFee(installationFee);
        invoice.setOtherCharges(otherCharges);
        invoice.setTotalAmount(totalAmount);
        invoice.setAmountPaid(BigDecimal.ZERO);
        invoice.setStatus(computeInvoiceStatus(invoice));
        invoice.setInvoiceType("subscription");
        invoice.setNotes(dto.getNotes());

        return invoiceRepository.save(invoice);
    }

    @Override
    @Transactional
    public Invoice updateInvoice(Long id, InvoiceDTO dto) {
        Invoice existing = getInvoiceById(id);
        Customer customer = getCustomerById(dto.getCustomerId());
        CustomerServiceEntity customerService = resolveCustomerServiceForInvoice(customer, dto.getCustomerServiceId());

        LocalDate billingMonth = dto.getDueDate() != null ? dto.getDueDate() : dto.getBillingMonth();
        if (billingMonth == null) {
            billingMonth = existing.getBillingMonth() != null
                    ? existing.getBillingMonth()
                    : resolveNextBillingAnchor(customer, customerService, id);
        }

        validateInvoiceUniqueness(dto.getCustomerId(), billingMonth, id);

        LocalDate dueDate = dto.getDueDate() != null
                ? dto.getDueDate()
                : resolveNextDueDate(customer, customerService, id, billingMonth);
        BigDecimal monthlyFee = dto.getMonthlyFee() != null
                ? dto.getMonthlyFee()
                : existing.getMonthlyFee() != null ? existing.getMonthlyFee()
                : customerService != null ? defaultAmount(customerService.getMonthlyFee()) : BigDecimal.ZERO;
        BigDecimal installationFee = dto.getInstallationFee() != null
                ? dto.getInstallationFee()
                : defaultAmount(existing.getInstallationFee());
        BigDecimal otherCharges = dto.getOtherCharges() != null
                ? dto.getOtherCharges()
                : defaultAmount(existing.getOtherCharges());
        BigDecimal totalAmount = monthlyFee.add(installationFee).add(otherCharges);

        existing.setCustomer(customer);
        existing.setCustomerService(customerService);
        existing.setBillingMonth(billingMonth);
        existing.setDueDate(dueDate);
        existing.setMonthlyFee(monthlyFee);
        existing.setInstallationFee(installationFee);
        existing.setOtherCharges(otherCharges);
        existing.setTotalAmount(totalAmount);
        existing.setInvoiceType(normalizeInvoiceType(existing.getInvoiceType()));
        existing.setNotes(dto.getNotes());

        if (!"cancelled".equalsIgnoreCase(existing.getStatus())) {
            existing.setStatus(computeInvoiceStatus(existing));
        }

        Invoice saved = invoiceRepository.save(existing);
        if ("paid".equalsIgnoreCase(saved.getStatus())) {
            ensureNextRecurringInvoice(saved.getCustomer(), saved.getCustomerService(), saved);
        }
        return saved;
    }
    
    @Override
    public Invoice getInvoiceById(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Invoice tidak ditemukan!"));
    }

    @Override
    @Transactional
    public InvoiceRowDTO getInvoiceRowById(Long id) {
        return billingInvoiceService.getInvoiceRowById(id);
    }

    @Override
    @Transactional
    public List<Invoice> getAllInvoices() {
        return synchronizeInvoiceStatuses(invoiceRepository.findAll());
    }

    @Override
    @Transactional
    public List<InvoiceRowDTO> getAllInvoiceRows() {
        return billingInvoiceService.getAllInvoiceRows();
    }

    @Override
    @Transactional
    public List<InvoiceRowDTO> getInvoiceRowsByCustomer(Long customerId) {
        return billingInvoiceService.getInvoiceRowsByCustomer(customerId);
    }
    
    @Override
    @Transactional
    public List<Invoice> getInvoicesByCustomer(Long customerId) {
        return synchronizeInvoiceStatuses(invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customerId));
    }
    
    @Override
    @Transactional
    public List<Invoice> getInvoicesByCustomerAndStatus(Long customerId, String status) {
        return synchronizeInvoiceStatuses(invoiceRepository.findByCustomerIdAndStatus(customerId, status)).stream()
                .filter(invoice -> status.equalsIgnoreCase(invoice.getStatus()))
                .toList();
    }

    @Override
    @Transactional
    public List<Invoice> getInvoicesByStatus(String status) {
        return synchronizeInvoiceStatuses(invoiceRepository.findAll()).stream()
                .filter(invoice -> status.equalsIgnoreCase(invoice.getStatus()))
                .sorted((left, right) -> {
                    LocalDate leftDate = left.getDueDate() != null ? left.getDueDate() : LocalDate.MIN;
                    LocalDate rightDate = right.getDueDate() != null ? right.getDueDate() : LocalDate.MIN;
                    return leftDate.compareTo(rightDate);
                })
                .toList();
    }

    @Override
    @Transactional
    public List<Invoice> getUnpaidInvoices() {
        return synchronizeInvoiceStatuses(invoiceRepository.findAll()).stream()
                .filter(invoice -> !"paid".equalsIgnoreCase(invoice.getStatus()) && !"cancelled".equalsIgnoreCase(invoice.getStatus()))
                .sorted((left, right) -> {
                    LocalDate leftDate = left.getDueDate() != null ? left.getDueDate() : LocalDate.MIN;
                    LocalDate rightDate = right.getDueDate() != null ? right.getDueDate() : LocalDate.MIN;
                    return leftDate.compareTo(rightDate);
                })
                .toList();
    }

    @Override
    @Transactional
    public List<InvoiceRowDTO> getUnpaidInvoiceRows() {
        return billingInvoiceService.getAllInvoiceRows().stream()
                .filter(invoice -> !"paid".equalsIgnoreCase(invoice.getStatus()) && !"cancelled".equalsIgnoreCase(invoice.getStatus()))
                .sorted((left, right) -> {
                    LocalDate leftDate = left.getDueDate() != null ? left.getDueDate() : LocalDate.MIN;
                    LocalDate rightDate = right.getDueDate() != null ? right.getDueDate() : LocalDate.MIN;
                    return leftDate.compareTo(rightDate);
                })
                .toList();
    }
    
    @Override
    @Transactional
    public List<Invoice> getInvoicesByCustomerYearAndMonth(Long customerId, Integer year, Integer month) {
        return synchronizeInvoiceStatuses(invoiceRepository.findByCustomerIdAndYearAndMonth(customerId, year, month));
    }

    @Override
    @Transactional
    public List<InvoiceRowDTO> getInvoiceRowsByCustomerYearAndMonth(Long customerId, Integer year, Integer month) {
        return billingInvoiceService.getInvoiceRowsByCustomer(customerId).stream()
                .filter(invoice -> matchesInvoicePeriod(invoice, year, month))
                .toList();
    }
    
    @Override
    @Transactional
    public List<Invoice> getInvoicesByCustomerYear(Long customerId, Integer year) {
        return synchronizeInvoiceStatuses(invoiceRepository.findByCustomerIdAndYear(customerId, year));
    }

    @Override
    @Transactional
    public List<InvoiceRowDTO> getInvoiceRowsByCustomerYear(Long customerId, Integer year) {
        return billingInvoiceService.getInvoiceRowsByCustomer(customerId).stream()
                .filter(invoice -> matchesInvoicePeriod(invoice, year, null))
                .toList();
    }
    
    @Override
    @Transactional
    public List<Invoice> getPaidInvoicesByCustomer(Long customerId) {
        return synchronizeInvoiceStatuses(invoiceRepository.findPaidInvoicesByCustomerId(customerId)).stream()
                .filter(invoice -> "paid".equalsIgnoreCase(invoice.getStatus()))
                .toList();
    }

    @Override
    @Transactional
    public List<InvoiceRowDTO> getPaidInvoiceRowsByCustomer(Long customerId) {
        return billingInvoiceService.getInvoiceRowsByCustomer(customerId).stream()
                .filter(invoice -> "paid".equalsIgnoreCase(invoice.getStatus()))
                .toList();
    }

    @Override
    @Transactional
    public List<Invoice> getPaidInvoices() {
        return synchronizeInvoiceStatuses(invoiceRepository.findAll()).stream()
                .filter(invoice -> "paid".equalsIgnoreCase(invoice.getStatus()))
                .toList();
    }

    @Override
    public List<Integer> getDistinctInvoiceYears() {
        return invoiceRepository.findAll().stream()
                .map(this::resolveInvoiceReferenceDate)
                .filter(Objects::nonNull)
                .map(LocalDate::getYear)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    @Override
    public List<Integer> getDistinctInvoiceMonthsByYear(Integer year) {
        if (year == null) {
            return List.of();
        }
        return invoiceRepository.findAll().stream()
                .map(this::resolveInvoiceReferenceDate)
                .filter(Objects::nonNull)
                .filter(referenceDate -> referenceDate.getYear() == year)
                .map(LocalDate::getMonthValue)
                .distinct()
                .sorted()
                .toList();
    }
    
    @Override
    public List<Integer> getDistinctYearsByCustomer(Long customerId) {
        return invoiceRepository.findDistinctYearsByCustomerId(customerId);
    }
    
    @Override
    public List<Integer> getDistinctMonthsByCustomerAndYear(Long customerId, Integer year) {
        return invoiceRepository.findDistinctMonthsByCustomerIdAndYear(customerId, year);
    }
    
    @Override
    @Transactional
    public Invoice payInvoice(Long invoiceId, BigDecimal amount, String paymentMethod, String notes) {
        billingQuickActionService.payInvoice(
                invoiceId,
                new com.netmaster.nmx.dto.QuickPayInvoiceRequest(amount, LocalDate.now(), paymentMethod, notes)
        );
        return getInvoiceById(invoiceId);
    }
    
    @Override
    @Transactional
    public Invoice cancelInvoice(Long invoiceId) {
        Invoice invoice = getInvoiceById(invoiceId);
        
        if ("paid".equals(invoice.getStatus())) {
            throw new RuntimeException("Invoice sudah lunas tidak dapat dibatalkan!");
        }
        
        invoice.setStatus("cancelled");
        return invoiceRepository.save(invoice);
    }

    @Override
    @Transactional
    public void deleteInvoice(Long invoiceId) {
        Invoice invoice = getInvoiceById(invoiceId);
        if ("paid".equals(invoice.getStatus())) {
            throw new RuntimeException("Invoice yang sudah lunas tidak dapat dihapus!");
        }
        invoiceRepository.delete(invoice);
    }

    private LocalDate resolveInvoiceReferenceDate(Invoice invoice) {
        if (invoice == null) {
            return null;
        }
        if (invoice.getBillingMonth() != null) {
            return invoice.getBillingMonth();
        }
        if (invoice.getDueDate() != null) {
            return invoice.getDueDate();
        }
        return invoice.getPaymentDate();
    }
    
    private BigDecimal defaultAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private Invoice activateCustomerAndRecordPayment(Customer customer, CustomerServiceEntity service) {
        return activateCustomerAndRecordPayment(customer, service, resolveActivationOptions(service, null));
    }

    private Invoice activateCustomerAndRecordPayment(Customer customer, CustomerServiceEntity service, ActivationOptions options) {
        service.setStatus("active");
        service.setActivationDate(options.activationDate());
        service.setInstallationFee(options.installationFee());
        customer.setStatus("active");
        customerRepository.save(customer);
        customerServiceEntityRepository.save(service);

        LocalDate effectiveDate = options.activationDate();
        Invoice activationInvoice = createOrUpdateActivationInvoice(customer, service, effectiveDate, true,
                options.paymentMethod(),
                "Pembayaran aktivasi pelanggan tercatat saat verifikasi.",
                "Invoice pembayaran aktivasi pelanggan dibuat otomatis saat aktivasi.",
                "activation");
        ensureNextRecurringInvoice(customer, service, activationInvoice);
        return activationInvoice;
    }

    private void backfillPaymentHistoryForEligibleCustomers() {
        customerRepository.findAll().forEach(customer -> backfillPaymentHistoryForCustomerIfNeeded(customer.getId()));
    }

    private void backfillPaymentHistoryForCustomerIfNeeded(Long customerId) {
        Customer customer = getCustomerById(customerId);
        if (!"active".equalsIgnoreCase(customer.getStatus())) {
            return;
        }

        CustomerServiceEntity service = customerServiceEntityRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customerId)
                .orElse(null);
        if (service == null || !"active".equalsIgnoreCase(service.getStatus())) {
            return;
        }

        List<Invoice> invoices = invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customerId);
        if (!invoices.isEmpty()) {
            return;
        }

        LocalDate effectiveDate = resolveBillingStartDate(customer, service);

        createOrUpdateActivationInvoice(customer, service, effectiveDate, true,
                "Backfill Aktivasi",
                "Pembayaran otomatis dibuat dari data aktivasi pelanggan yang sudah ada.",
                "Invoice backfill dibuat otomatis agar history pembayaran sinkron.",
                "activation");
    }

    private Invoice createOrUpdateActivationInvoice(
            Customer customer,
            CustomerServiceEntity service,
            LocalDate effectiveDate,
            boolean markPaid,
            String paymentMethod,
            String paymentNotes,
            String notes,
            String invoiceType
    ) {
        LocalDate baseDate = effectiveDate != null ? effectiveDate : LocalDate.now();
        LocalDate billingMonth = baseDate.withDayOfMonth(1);
        String normalizedInvoiceType = normalizeInvoiceType(invoiceType);
        Optional<Invoice> existingInvoice = invoiceRepository.findByCustomerCodeAndBillingMonth(customer.getCustomerCode(), billingMonth);

        if (existingInvoice.isPresent()) {
            Invoice invoice = existingInvoice.get();
            boolean activationInvoice = "activation".equalsIgnoreCase(normalizedInvoiceType);
            BigDecimal monthlyFee = activationInvoice ? BigDecimal.ZERO : defaultAmount(service.getMonthlyFee());
            BigDecimal installationFee = defaultAmount(service.getInstallationFee());
            BigDecimal otherCharges = defaultAmount(invoice.getOtherCharges());
            BigDecimal totalAmount = monthlyFee.add(installationFee).add(otherCharges);
            invoice.setCustomer(customer);
            invoice.setCustomerService(service);
            invoice.setMonthlyFee(monthlyFee);
            invoice.setInstallationFee(installationFee);
            invoice.setOtherCharges(otherCharges);
            invoice.setTotalAmount(totalAmount);
            invoice.setDueDate(baseDate);
            invoice.setInvoiceType(normalizedInvoiceType);
            invoice.setNotes(notes);

            if (markPaid) {
                invoice.setAmountPaid(defaultAmount(invoice.getTotalAmount()));
                invoice.setPaymentDate(baseDate);
                invoice.setPaymentMethod(paymentMethod);
                invoice.setPaymentNotes(paymentNotes);
                invoice.setStatus("paid");
            } else {
                invoice.setStatus(computeInvoiceStatus(invoice));
            }
            Invoice saved = invoiceRepository.save(invoice);
            if (markPaid) {
                paymentManagementService.ensurePaymentRecordForInvoice(saved, saved.getTotalAmount(), baseDate, paymentMethod, paymentNotes);
            }
            return saved;
        }

        boolean activationInvoice = "activation".equalsIgnoreCase(normalizedInvoiceType);
        BigDecimal monthlyFee = activationInvoice ? BigDecimal.ZERO : defaultAmount(service.getMonthlyFee());
        BigDecimal installationFee = defaultAmount(service.getInstallationFee());
        BigDecimal totalAmount = monthlyFee.add(installationFee);

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invoiceNumberService.generate());
        invoice.setCustomer(customer);
        invoice.setCustomerService(service);
        invoice.setBillingMonth(billingMonth);
        invoice.setDueDate(baseDate);
        invoice.setMonthlyFee(monthlyFee);
        invoice.setInstallationFee(installationFee);
        invoice.setOtherCharges(BigDecimal.ZERO);
        invoice.setTotalAmount(totalAmount);
        invoice.setAmountPaid(markPaid ? totalAmount : BigDecimal.ZERO);
        invoice.setStatus(markPaid ? "paid" : computeInvoiceStatus(invoice));
        invoice.setPaymentDate(markPaid ? baseDate : null);
        invoice.setPaymentMethod(markPaid ? paymentMethod : null);
        invoice.setInvoiceType(normalizedInvoiceType);
        invoice.setPaymentNotes(markPaid ? paymentNotes : null);
        invoice.setNotes(notes);
        Invoice saved = invoiceRepository.save(invoice);
        if (markPaid) {
            paymentManagementService.ensurePaymentRecordForInvoice(saved, saved.getTotalAmount(), baseDate, paymentMethod, paymentNotes);
        }
        return saved;
    }

    private ActivationOptions resolveActivationOptions(CustomerServiceEntity service, CustomerActivationRequest request) {
        BigDecimal installationFee = request != null && request.getInstallationFee() != null
                ? request.getInstallationFee()
                : defaultAmount(service != null ? service.getInstallationFee() : null);
        if (installationFee.signum() < 0) {
            throw new RuntimeException("Biaya pemasangan tidak boleh negatif!");
        }

        LocalDate activationDate = request != null && request.getActivationDate() != null
                ? request.getActivationDate()
                : service != null && service.getActivationDate() != null ? service.getActivationDate() : LocalDate.now();

        String paymentMethod = normalizeActivationPaymentMethod(request != null ? request.getPaymentMethod() : null);
        return new ActivationOptions(installationFee, paymentMethod, activationDate);
    }

    private String normalizeActivationPaymentMethod(String paymentMethod) {
        if (!StringUtils.hasText(paymentMethod)) {
            return "Transfer";
        }

        String normalized = paymentMethod.trim().toLowerCase();
        return switch (normalized) {
            case "transfer" -> "Transfer";
            case "cash" -> "Cash";
            default -> throw new RuntimeException("Metode pembayaran aktivasi tidak valid!");
        };
    }

    private String normalizeInvoiceType(String invoiceType) {
        if (!StringUtils.hasText(invoiceType)) {
            return "subscription";
        }

        String normalized = invoiceType.trim().toLowerCase();
        return switch (normalized) {
            case "activation" -> "activation";
            case "subscription" -> "subscription";
            default -> "subscription";
        };
    }

    private String resolveInvoiceTypeLabel(Invoice invoice) {
        return switch (normalizeInvoiceType(invoice != null ? invoice.getInvoiceType() : null)) {
            case "activation" -> "Pembayaran Aktivasi Pelanggan";
            default -> "Pembayaran Langganan Bulanan";
        };
    }

    private InvoiceRowDTO toInvoiceRow(Invoice invoice) {
        BigDecimal totalAmount = defaultAmount(invoice.getTotalAmount());
        BigDecimal amountPaid = defaultAmount(invoice.getAmountPaid());
        BigDecimal outstandingAmount = totalAmount.subtract(amountPaid).max(BigDecimal.ZERO);

        Customer customer = invoice.getCustomer();
        CustomerServiceEntity service = invoice.getCustomerService();

        return new InvoiceRowDTO(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                customer != null ? customer.getId() : null,
                customer != null ? customer.getCustomerCode() : null,
                customer != null ? customer.getFullName() : null,
                service != null ? service.getId() : null,
                invoice.getBillingMonth(),
                invoice.getDueDate(),
                defaultAmount(invoice.getMonthlyFee()),
                defaultAmount(invoice.getInstallationFee()),
                defaultAmount(invoice.getOtherCharges()),
                totalAmount,
                amountPaid,
                outstandingAmount,
                invoice.getStatus(),
                invoice.getPaymentDate(),
                invoice.getPaymentMethod(),
                normalizeInvoiceType(invoice.getInvoiceType()),
                resolveInvoiceTypeLabel(invoice),
                invoice.getPaymentNotes(),
                invoice.getNotes()
        );
    }

    private void validateCustomerUniqueness(CustomerRegistrationDTO dto, Long currentCustomerId) {
        if (StringUtils.hasText(dto.getKtpNumber())) {
            customerRepository.findByKtpNumber(dto.getKtpNumber())
                    .filter(customer -> !customer.getId().equals(currentCustomerId))
                    .ifPresent(customer -> {
                        throw new RuntimeException("Nomor KTP sudah terdaftar!");
                    });
        }

        customerRepository.findByPhone(dto.getPhone())
                .filter(customer -> !customer.getId().equals(currentCustomerId))
                .ifPresent(customer -> {
                    throw new RuntimeException("Nomor telepon sudah terdaftar!");
                });

        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            customerRepository.findByEmail(dto.getEmail())
                    .filter(customer -> !customer.getId().equals(currentCustomerId))
                    .ifPresent(customer -> {
                        throw new RuntimeException("Email sudah terdaftar!");
                    });
        }
    }

    private void validateInvoiceUniqueness(Long customerId, LocalDate billingMonth, Long invoiceId) {
        if (customerId == null) {
            throw new RuntimeException("Pelanggan wajib dipilih!");
        }

        if (billingMonth == null) {
            return;
        }

        invoiceRepository.findByCustomerCodeAndBillingMonth(getCustomerById(customerId).getCustomerCode(), billingMonth)
                .filter(invoice -> !invoice.getId().equals(invoiceId))
                .ifPresent(invoice -> {
                    throw new RuntimeException("Invoice untuk pelanggan dan periode tagihan tersebut sudah ada!");
                });
    }

    private CustomerServiceEntity resolveCustomerServiceForInvoice(Customer customer, Long customerServiceId) {
        if (customerServiceId != null) {
            return customerServiceEntityRepository.findById(customerServiceId)
                    .orElseThrow(() -> new RuntimeException("Layanan tidak ditemukan!"));
        }

        if (customer == null || customer.getId() == null) {
            return null;
        }

        return customerServiceEntityRepository.findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .orElse(null);
    }

    private LocalDate resolveNextBillingAnchor(Customer customer, CustomerServiceEntity service, Long currentInvoiceId) {
        LocalDate referenceDate = resolveRecurringReferenceDate(customer, service, currentInvoiceId);
        return referenceDate.plusMonths(1);
    }

    private LocalDate resolveNextDueDate(Customer customer, CustomerServiceEntity service, Long currentInvoiceId, LocalDate billingMonth) {
        if (billingMonth != null) {
            return billingMonth;
        }
        return resolveNextBillingAnchor(customer, service, currentInvoiceId);
    }

    private LocalDate resolveRecurringReferenceDate(Customer customer, CustomerServiceEntity service, Long currentInvoiceId) {
        if (customer != null && customer.getId() != null) {
            LocalDate latestDueDate = findLatestScheduledDate(customer.getId(), currentInvoiceId);
            if (latestDueDate != null) {
                return latestDueDate;
            }
        }
        return resolveBillingStartDate(customer, service);
    }

    private LocalDate resolveBillingStartDate(Customer customer, CustomerServiceEntity service) {
        // Billing awal harus mengikuti tanggal registrasi pelanggan,
        // bukan tanggal aktivasi layanan.
        if (customer != null && customer.getRegistrationDate() != null) {
            return customer.getRegistrationDate();
        }
        if (service != null && service.getInstallationDate() != null) {
            return service.getInstallationDate();
        }
        if (service != null && service.getActivationDate() != null) {
            return service.getActivationDate();
        }
        return LocalDate.now();
    }

    private LocalDate findLatestScheduledDate(Long customerId, Long currentInvoiceId) {
        return invoiceRepository.findByCustomerIdOrderByBillingMonthDesc(customerId).stream()
                .filter(invoice -> currentInvoiceId == null || !invoice.getId().equals(currentInvoiceId))
                .filter(invoice -> !"cancelled".equalsIgnoreCase(invoice.getStatus()))
                .map(invoice -> invoice.getDueDate() != null ? invoice.getDueDate() : invoice.getBillingMonth())
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    private void ensureNextRecurringInvoice(Customer customer, CustomerServiceEntity service, Invoice paidInvoice) {
        if (customer == null || service == null || paidInvoice == null) {
            return;
        }

        if (!"active".equalsIgnoreCase(customer.getStatus()) || !"active".equalsIgnoreCase(service.getStatus())) {
            return;
        }

        LocalDate referenceDate = paidInvoice.getDueDate() != null
                ? paidInvoice.getDueDate()
                : paidInvoice.getBillingMonth() != null ? paidInvoice.getBillingMonth() : resolveBillingStartDate(customer, service);
        LocalDate nextDueDate = referenceDate.plusMonths(1);
        LocalDate nextBillingAnchor = nextDueDate;

        Optional<Invoice> existingInvoice = invoiceRepository.findByCustomerCodeAndBillingMonth(customer.getCustomerCode(), nextBillingAnchor);
        BigDecimal monthlyFee = defaultAmount(service.getMonthlyFee());
        if (monthlyFee.signum() <= 0) {
            return;
        }

        if (existingInvoice.isPresent()) {
            Invoice invoice = existingInvoice.get();
            invoice.setCustomer(customer);
            invoice.setCustomerService(service);
            invoice.setBillingMonth(nextBillingAnchor);
            invoice.setDueDate(nextDueDate);
            invoice.setMonthlyFee(monthlyFee);
            invoice.setInstallationFee(BigDecimal.ZERO);
            invoice.setOtherCharges(defaultAmount(invoice.getOtherCharges()));
            invoice.setTotalAmount(monthlyFee.add(defaultAmount(invoice.getOtherCharges())));
            invoice.setInvoiceType("subscription");
            if (!"paid".equalsIgnoreCase(invoice.getStatus()) && !"cancelled".equalsIgnoreCase(invoice.getStatus())) {
                invoice.setStatus(computeInvoiceStatus(invoice));
            }
            if (!StringUtils.hasText(invoice.getNotes())) {
                invoice.setNotes("Tagihan rutin otomatis. Jatuh tempo setiap bulan pada tanggal yang sama.");
            }
            invoiceRepository.save(invoice);
            return;
        }

        Invoice nextInvoice = new Invoice();
        nextInvoice.setInvoiceNumber(invoiceNumberService.generate());
        nextInvoice.setCustomer(customer);
        nextInvoice.setCustomerService(service);
        nextInvoice.setBillingMonth(nextBillingAnchor);
        nextInvoice.setDueDate(nextDueDate);
        nextInvoice.setMonthlyFee(monthlyFee);
        nextInvoice.setInstallationFee(BigDecimal.ZERO);
        nextInvoice.setOtherCharges(BigDecimal.ZERO);
        nextInvoice.setTotalAmount(monthlyFee);
        nextInvoice.setAmountPaid(BigDecimal.ZERO);
        nextInvoice.setInvoiceType("subscription");
        nextInvoice.setStatus("pending");
        nextInvoice.setNotes("Tagihan rutin otomatis. Jatuh tempo setiap bulan pada tanggal yang sama.");
        invoiceRepository.save(nextInvoice);
    }

    private List<Invoice> synchronizeInvoiceStatuses(List<Invoice> invoices) {
        List<Invoice> normalizedInvoices = new ArrayList<>();
        for (Invoice invoice : invoices) {
            normalizedInvoices.add(synchronizeInvoiceStatus(invoice));
        }
        return normalizedInvoices;
    }

    private Invoice synchronizeInvoiceStatus(Invoice invoice) {
        if (invoice == null || "cancelled".equalsIgnoreCase(invoice.getStatus())) {
            return invoice;
        }

        String computedStatus = computeInvoiceStatus(invoice);
        if (!computedStatus.equalsIgnoreCase(invoice.getStatus())) {
            invoice.setStatus(computedStatus);
            return invoiceRepository.save(invoice);
        }
        return invoice;
    }

    private String computeInvoiceStatus(Invoice invoice) {
        if (invoice == null) {
            return "pending";
        }
        if ("cancelled".equalsIgnoreCase(invoice.getStatus())) {
            return "cancelled";
        }

        BigDecimal totalAmount = defaultAmount(invoice.getTotalAmount());
        BigDecimal amountPaid = defaultAmount(invoice.getAmountPaid());
        if (totalAmount.signum() == 0 || amountPaid.compareTo(totalAmount) >= 0) {
            return "paid";
        }
        if (invoice.getDueDate() != null && invoice.getDueDate().isBefore(LocalDate.now())) {
            return "overdue";
        }
        if (amountPaid.signum() > 0) {
            return "partial";
        }
        return "pending";
    }

    private StatusPembayaran parseStatusPembayaranFilter(String statusFilter) {
        if (!StringUtils.hasText(statusFilter) || "ALL".equalsIgnoreCase(statusFilter)) {
            return null;
        }

        try {
            return StatusPembayaran.valueOf(statusFilter.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Status pembayaran tidak valid. Gunakan ALL, BELUM_LUNAS, LUNAS, atau JATUH_TEMPO."
            );
        }
    }

    private boolean matchesInvoicePeriod(InvoiceRowDTO invoice, Integer year, Integer month) {
        if (invoice == null) {
            return false;
        }
        LocalDate referenceDate = invoice.getBillingMonth() != null
                ? invoice.getBillingMonth()
                : invoice.getDueDate() != null ? invoice.getDueDate() : invoice.getPaymentDate();
        if (referenceDate == null) {
            return false;
        }
        if (year != null && referenceDate.getYear() != year) {
            return false;
        }
        return month == null || referenceDate.getMonthValue() == month;
    }

    private String normalizeCustomerStatus(String status) {
        String normalized = trimToNull(status);
        if (!StringUtils.hasText(normalized)) {
            return "pending";
        }

        normalized = normalized.toLowerCase();
        if ("active".equals(normalized)) {
            return "active";
        }
        if ("suspended".equals(normalized)) {
            return "suspended";
        }
        if ("inactive".equals(normalized) || "nonaktif".equals(normalized)) {
            return "inactive";
        }
        return "pending";
    }

    private List<Customer> resolveCustomersByPaymentStatus(StatusPembayaran filter, LocalDate today) {
        if (filter == null) {
            return customerRepository.findAll();
        }

        Set<Long> customerIds = switch (filter) {
            case BELUM_LUNAS -> new HashSet<>(invoiceRepository.findCustomerIdsByBelumLunas(today, today.plusDays(PAYMENT_REMINDER_WINDOW_DAYS)));
            case JATUH_TEMPO -> new HashSet<>(invoiceRepository.findCustomerIdsByJatuhTempo(today));
            case LUNAS -> {
                Set<Long> paidCustomerIds = new HashSet<>(invoiceRepository.findCustomerIdsByLunas());
                paidCustomerIds.removeAll(new HashSet<>(invoiceRepository.findCustomerIdsWithOutstanding()));
                yield paidCustomerIds;
            }
        };

        if (customerIds.isEmpty()) {
            return List.of();
        }
        return customerRepository.findAllById(customerIds);
    }

    private boolean matchesHistoryStatusFilter(String paymentStatus, StatusPembayaran filter) {
        if (filter == null) {
            return true;
        }
        return switch (filter) {
            case BELUM_LUNAS -> "unpaid".equalsIgnoreCase(paymentStatus);
            case LUNAS -> "paid".equalsIgnoreCase(paymentStatus);
            case JATUH_TEMPO -> "overdue".equalsIgnoreCase(paymentStatus);
        };
    }

    private String resolveCustomerPaymentStatus(
            Customer customer,
            CustomerServiceEntity service,
            List<Invoice> invoices,
            LocalDate today
    ) {
        boolean activated = isCustomerActivated(customer, service);
        if (invoices == null || invoices.isEmpty()) {
            return activated ? "paid" : "unpaid";
        }

        boolean hasOverdue = invoices.stream().anyMatch(invoice -> isOverdueInvoice(invoice, today));
        if (hasOverdue) {
            return "overdue";
        }

        boolean hasUpcomingOutstanding = invoices.stream().anyMatch(invoice -> isWithinReminderWindow(invoice, today));
        if (hasUpcomingOutstanding) {
            return "unpaid";
        }
        boolean hasOutstanding = invoices.stream().anyMatch(this::isOutstandingInvoice);
        if (hasOutstanding) {
            return "unpaid";
        }

        boolean hasPaid = invoices.stream().anyMatch(invoice -> "paid".equalsIgnoreCase(invoice.getStatus()));
        return hasPaid ? "paid" : "unpaid";
    }

    private boolean isCustomerActivated(Customer customer, CustomerServiceEntity service) {
        return "active".equalsIgnoreCase(normalizeCustomerStatus(customer != null ? customer.getStatus() : null))
                || (service != null && "active".equalsIgnoreCase(trimToNull(service.getStatus())));
    }

    private boolean isOverdueInvoice(Invoice invoice, LocalDate today) {
        if (invoice == null || !isOutstandingInvoice(invoice)) {
            return false;
        }
        return invoice.getDueDate() != null && invoice.getDueDate().isBefore(today);
    }

    private boolean isWithinReminderWindow(Invoice invoice, LocalDate today) {
        if (invoice == null || !isOutstandingInvoice(invoice) || today == null) {
            return false;
        }
        if (invoice.getDueDate() == null) {
            return true;
        }
        return !today.isBefore(invoice.getDueDate().minusDays(PAYMENT_REMINDER_WINDOW_DAYS))
                && !invoice.getDueDate().isBefore(today);
    }

    private boolean isOutstandingInvoice(Invoice invoice) {
        if (invoice == null) {
            return false;
        }

        String status = invoice.getStatus() != null ? invoice.getStatus().toLowerCase() : "";
        return !"paid".equals(status) && !"cancelled".equals(status);
    }

    private BigDecimal resolveOutstandingAmount(Invoice invoice) {
        if (invoice == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal totalAmount = defaultAmount(invoice.getTotalAmount());
        BigDecimal amountPaid = defaultAmount(invoice.getAmountPaid());
        BigDecimal outstanding = totalAmount.subtract(amountPaid);
        return outstanding.signum() > 0 ? outstanding : BigDecimal.ZERO;
    }

    private void releaseOdpPort(CustomerServiceEntity service) {
        Odp odp = service.getOdp();
        if (odp == null) {
            return;
        }

        int usedPort = odp.getUsedPort() != null ? odp.getUsedPort() : 0;
        if (usedPort > 0) {
            odp.setUsedPort(usedPort - 1);
            odpRepository.save(odp);
        }
    }

    private CustomerServiceEntity upsertCustomerService(Customer customer, CustomerRegistrationDTO dto) {
        CustomerServiceEntity service = customerServiceEntityRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .orElseGet(CustomerServiceEntity::new);
        service.setCustomer(customer);
        return upsertCustomerService(service, dto);
    }

    private CustomerServiceEntity upsertCustomerService(CustomerServiceEntity service, CustomerRegistrationDTO dto) {
        Odp currentOdp = service.getOdp();
        Integer currentPort = service.getOdpPort();
        Odp selectedOdp = resolveSelectedOdp(dto);
        Integer selectedPort = selectedOdp != null ? dto.getOdpPort() : null;

        syncOdpAllocation(service, selectedOdp, selectedPort, currentOdp, currentPort);

        InternetPackage selectedPackage = null;
        if (dto.getPackageId() != null) {
            selectedPackage = internetPackageRepository.findById(dto.getPackageId())
                    .orElseThrow(() -> new RuntimeException("Paket tidak ditemukan!"));
        }
        service.setInternetPackage(selectedPackage);

        if (dto.getServiceTypeId() != null) {
            ServiceType serviceType = serviceTypeRepository.findById(dto.getServiceTypeId())
                    .orElseThrow(() -> new RuntimeException("Tipe layanan tidak ditemukan!"));
            service.setServiceType(serviceType);
        } else {
            service.setServiceType(null);
        }

        if (dto.getTechnicianId() != null) {
            Technician technician = technicianRepository.findById(dto.getTechnicianId())
                    .orElseThrow(() -> new RuntimeException("Teknisi tidak ditemukan!"));
            if (!Boolean.TRUE.equals(technician.getIsActive())) {
                throw new RuntimeException("Teknisi tidak aktif!");
            }
            service.setTechnician(technician);
        } else {
            service.setTechnician(null);
        }

        service.setOdp(selectedOdp);
        service.setOdpPort(selectedPort);
        service.setOntSerial(trimToNull(dto.getOntSerial()));
        service.setOntBrand(trimToNull(dto.getOntBrand()));
        service.setPppoeUsername(trimToNull(dto.getPppoeUsername()));
        service.setPppoePassword(trimToNull(dto.getPppoePassword()));
        service.setIpAddress(trimToNull(dto.getIpAddress()));
        service.setMacAddress(trimToNull(dto.getMacAddress()));
        service.setMonthlyFee(resolveMonthlyFee(dto, selectedPackage));
        service.setInstallationFee(dto.getInstallationFee() != null ? dto.getInstallationFee() : BigDecimal.ZERO);
        service.setInstallationDate(dto.getInstallationDate() != null ? dto.getInstallationDate() : LocalDate.now());
        if (!StringUtils.hasText(service.getStatus())) {
            service.setStatus("pending");
        }
        service.setNotes(trimToNull(dto.getNotes()));

        return customerServiceEntityRepository.save(service);
    }

    private Odp resolveSelectedOdp(CustomerRegistrationDTO dto) {
        if (dto.getOdpId() == null) {
            return null;
        }

        return resolveSelectedOdp(dto.getOdpId(), dto.getOdcId(), dto.getServerId(), dto.getCompanyProfileId());
    }

    private Odp resolveSelectedOdp(Long odpId, Long odcId, Long serverId, Long companyProfileId) {
        if (odpId == null) {
            return null;
        }

        Odp odp = odpRepository.findById(odpId)
                .orElseThrow(() -> new RuntimeException("ODP tidak ditemukan!"));

        if (!"ODP".equalsIgnoreCase(odp.getNodeType())) {
            throw new RuntimeException("Node yang dipilih bukan data ODP.");
        }
        if (!Boolean.TRUE.equals(odp.getIsActive())) {
            throw new RuntimeException("ODP tidak aktif!");
        }
        if (odcId != null) {
            if (odp.getOdc() == null || !odcId.equals(odp.getOdc().getId())) {
                throw new RuntimeException("ODP tidak sesuai dengan ODC yang dipilih.");
            }
        }
        if (serverId != null) {
            Long odpServerId = odp.getOdc() != null && odp.getOdc().getServer() != null
                    ? odp.getOdc().getServer().getId()
                    : null;
            if (!serverId.equals(odpServerId)) {
                throw new RuntimeException("ODP tidak sesuai dengan Server/OLT yang dipilih.");
            }
        }
        if (companyProfileId != null) {
            Long companyId = odp.getCompanyProfile() != null ? odp.getCompanyProfile().getId() : null;
            if (companyId != null && !companyProfileId.equals(companyId)) {
                throw new RuntimeException("ODP tidak sesuai dengan wilayah/company yang dipilih.");
            }
        }

        return odp;
    }

    private void syncOdpAllocation(
            CustomerServiceEntity service,
            Odp selectedOdp,
            Integer selectedPort,
            Odp currentOdp,
            Integer currentPort
    ) {
        boolean odpChanged = !sameId(currentOdp, selectedOdp);
        boolean portChanged = !sameInteger(currentPort, selectedPort);

        if (!odpChanged && !portChanged) {
            return;
        }

        if (selectedOdp != null) {
            int available = (selectedOdp.getCapacity() != null ? selectedOdp.getCapacity() : 0)
                    - (selectedOdp.getUsedPort() != null ? selectedOdp.getUsedPort() : 0);
            if (odpChanged && available <= 0) {
                throw new RuntimeException("ODP sudah penuh!");
            }
            if (selectedPort != null) {
                customerServiceEntityRepository.findByOdpIdAndOdpPort(selectedOdp.getId(), selectedPort)
                        .filter(existing -> service.getId() == null || !existing.getId().equals(service.getId()))
                        .ifPresent(existing -> {
                            throw new RuntimeException("Port ODP sudah digunakan!");
                        });
            }
        }

        if (odpChanged && currentOdp != null) {
            decrementOdpPort(currentOdp);
        }

        if (odpChanged && selectedOdp != null) {
            incrementOdpPort(selectedOdp);
        }
    }

    private void incrementOdpPort(Odp odp) {
        int usedPort = odp.getUsedPort() != null ? odp.getUsedPort() : 0;
        odp.setUsedPort(usedPort + 1);
        odpRepository.save(odp);
    }

    private void decrementOdpPort(Odp odp) {
        int usedPort = odp.getUsedPort() != null ? odp.getUsedPort() : 0;
        if (usedPort > 0) {
            odp.setUsedPort(usedPort - 1);
            odpRepository.save(odp);
        }
    }

    private BigDecimal resolveMonthlyFee(CustomerRegistrationDTO dto, InternetPackage selectedPackage) {
        if (dto.getMonthlyFee() != null) {
            return dto.getMonthlyFee();
        }
        if (selectedPackage != null && selectedPackage.getPrice() != null) {
            return selectedPackage.getPrice();
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal parseDecimalOrNull(String value) {
        String normalized = trimToNull(value);
        return normalized != null ? new BigDecimal(normalized) : null;
    }

    private boolean sameId(Odp left, Odp right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.getId() != null && left.getId().equals(right.getId());
    }

    private boolean sameInteger(Integer left, Integer right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    private void applyTechnicianRequest(Technician technician, TechnicianRequest request) {
        String name = trimToNull(request.getName());
        String area = trimToNull(request.getArea());
        String phone = trimToNull(request.getPhone());
        String status = normalizeTechnicianStatus(request.getStatus());

        if (!StringUtils.hasText(name)) {
            throw new RuntimeException("Nama teknisi wajib diisi!");
        }
        if (!StringUtils.hasText(area)) {
            throw new RuntimeException("Area kerja wajib diisi!");
        }
        if (!StringUtils.hasText(phone)) {
            throw new RuntimeException("Nomor HP wajib diisi!");
        }

        technician.setName(name);
        technician.setArea(area);
        technician.setPhone(phone);
        technician.setStatus(status);
        technician.setIsActive(!"inactive".equals(status));
    }

    private String normalizeTechnicianStatus(String status) {
        String normalized = trimToNull(status);
        if (!StringUtils.hasText(normalized)) {
            return "active";
        }

        normalized = normalized.toLowerCase();
        if (!List.of("active", "busy", "inactive").contains(normalized)) {
            throw new RuntimeException("Status teknisi tidak valid!");
        }
        return normalized;
    }

    private TechnicianView toTechnicianView(Technician technician) {
        return TechnicianView.builder()
                .id(technician.getId())
                .name(technician.getName())
                .initials(buildInitials(technician.getName()))
                .area(technician.getArea())
                .phone(technician.getPhone())
                .status(normalizeTechnicianStatus(technician.getStatus()))
                .isActive(Boolean.TRUE.equals(technician.getIsActive()))
                .activeTickets(ticketRepository.countActiveByAssignedTechnicianId(technician.getId()))
                .rating(0.0d)
                .build();
    }

    private String buildInitials(String name) {
        if (!StringUtils.hasText(name)) {
            return "-";
        }

        String[] parts = name.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank()) {
                initials.append(Character.toUpperCase(part.charAt(0)));
            }
            if (initials.length() == 2) {
                break;
            }
        }
        return initials.isEmpty() ? "-" : initials.toString();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void syncServersFromActiveOlts() {
        List<OltDevice> activeOlts = oltDeviceRepository.findByIsActiveTrue();
        if (activeOlts.isEmpty()) {
            log.debug("syncServersFromActiveOlts: tidak ada OLT aktif.");
            return;
        }

        log.info("syncServersFromActiveOlts: mulai sync {} OLT aktif.", activeOlts.size());
        for (OltDevice oltDevice : activeOlts) {
            String resolvedPopName = trimToNull(oltDevice.getLocation());
            if (!StringUtils.hasText(resolvedPopName)) {
                resolvedPopName = trimToNull(oltDevice.getName());
            }

            String resolvedIpAddress = trimToNull(oltDevice.getVpnIpAddress());
            if (!StringUtils.hasText(resolvedIpAddress)) {
                resolvedIpAddress = trimToNull(oltDevice.getIpAddress());
            }

            if (!StringUtils.hasText(resolvedPopName) || !StringUtils.hasText(resolvedIpAddress)) {
                log.debug("syncServersFromActiveOlts: skip OLT id={} (nama/ip kosong).", oltDevice.getId());
                continue;
            }

            final String popName = resolvedPopName;
            final String ipAddress = resolvedIpAddress;

            Optional<Server> existingServer = serverRepository.findByNameIgnoreCase(popName)
                    .or(() -> serverRepository.findByIpAddress(ipAddress));
            if (existingServer.isEmpty()) {
                log.debug("syncServersFromActiveOlts: server tidak ditemukan untuk pop='{}', ip='{}'.", popName, ipAddress);
                continue;
            }
            Server server = existingServer.get();

            server.setName(popName);
            server.setIpAddress(ipAddress);
            server.setLocation(trimToNull(oltDevice.getLocation()));
            server.setIsActive(true);
            serverRepository.save(server);
            log.info("syncServersFromActiveOlts: update server id={} pop='{}' ip='{}'.", server.getId(), popName, ipAddress);
        }
    }

    private void ensureRegistrationServiceTypes() {
        REGISTRATION_SERVICE_TYPES.forEach((name, description) ->
                serviceTypeRepository.findByNameIgnoreCase(name).orElseGet(() -> {
                    ServiceType serviceType = new ServiceType();
                    serviceType.setName(name);
                    serviceType.setDescription(description);
                    serviceType.setIsActive(true);
                    return serviceTypeRepository.save(serviceType);
                })
        );
    }
}

