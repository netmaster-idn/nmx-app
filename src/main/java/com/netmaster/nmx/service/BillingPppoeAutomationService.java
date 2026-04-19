package com.netmaster.nmx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netmaster.nmx.model.Customer;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.model.Invoice;
import com.netmaster.nmx.model.MikrotikDevice;
import com.netmaster.nmx.model.Odc;
import com.netmaster.nmx.model.Odp;
import com.netmaster.nmx.model.Payment;
import com.netmaster.nmx.model.PppoeActionLog;
import com.netmaster.nmx.model.Server;
import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import com.netmaster.nmx.repository.MikrotikDeviceRepository;
import com.netmaster.nmx.repository.PppoeActionLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingPppoeAutomationService {

    private final CustomerRepository customerRepository;
    private final CustomerServiceEntityRepository customerServiceRepository;
    private final MikrotikDeviceRepository mikrotikDeviceRepository;
    private final MikrotikConnectionService mikrotikConnectionService;
    private final MikrotikRouterOsApiClient mikrotikRouterOsApiClient;
    private final PppoeActionLogRepository actionLogRepository;
    private final BillingOperatorContextService operatorContextService;
    private final BillingAuditTrailService billingAuditTrailService;
    private final BillingStatusSupport billingStatusSupport;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public String resolveCurrentState(Long customerId) {
        Customer customer = customerRepository.findById(customerId).orElse(null);
        CustomerServiceEntity service = latestService(customerId);
        if (customer == null || service == null || !StringUtils.hasText(service.getPppoeUsername())) {
            return billingStatusSupport.normalizePppoeStatus(customer != null ? customer.getPppoeStatus() : null);
        }

        try {
            MikrotikDevice device = resolveMikrotik(service);
            if (device == null) {
                return billingStatusSupport.normalizePppoeStatus(customer.getPppoeStatus());
            }
            String status = mikrotikRouterOsApiClient.getPppSecretStatus(
                    resolveTarget(device),
                    device.resolveApiUsername(),
                    device.resolveApiPassword(),
                    service.getPppoeUsername()
            );
            return billingStatusSupport.normalizePppoeStatus(status);
        } catch (Exception ex) {
            log.debug("Failed to resolve PPPoE state for customer {}: {}", customerId, ex.getMessage());
            return billingStatusSupport.normalizePppoeStatus(customer.getPppoeStatus());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> disableCustomer(Long customerId,
                                               Invoice invoice,
                                               Payment payment,
                                               String reason,
                                               String executionMode) {
        return toggleCustomer(customerId, invoice, payment, reason, executionMode, true);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> enableCustomer(Long customerId,
                                              Invoice invoice,
                                              Payment payment,
                                              String reason,
                                              String executionMode) {
        return toggleCustomer(customerId, invoice, payment, reason, executionMode, false);
    }

    private Map<String, Object> toggleCustomer(Long customerId,
                                               Invoice invoice,
                                               Payment payment,
                                               String reason,
                                               String executionMode,
                                               boolean disable) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Pelanggan tidak ditemukan"));
        CustomerServiceEntity service = Optional.ofNullable(invoice)
                .map(Invoice::getCustomerService)
                .orElseGet(() -> latestService(customerId));
        if (service == null || !StringUtils.hasText(service.getPppoeUsername())) {
            throw new IllegalArgumentException("PPPoE username pelanggan belum tersedia");
        }

        String currentStatus = billingStatusSupport.normalizePppoeStatus(customer.getPppoeStatus());
        if (disable && "disabled".equals(currentStatus)) {
            return Map.of(
                    "customerId", customer.getId(),
                    "pppoeUsername", service.getPppoeUsername(),
                    "status", "skipped",
                    "message", "PPPoE secret sudah disabled"
            );
        }
        if (!disable && "active".equals(currentStatus)) {
            return Map.of(
                    "customerId", customer.getId(),
                    "pppoeUsername", service.getPppoeUsername(),
                    "status", "skipped",
                    "message", "PPPoE secret sudah enabled"
            );
        }

        if (!tableExists("pppoe_action_logs")) {
            return toggleCustomerWithoutLog(customer, service, reason, disable);
        }

        PppoeActionLog actionLog = new PppoeActionLog();
        actionLog.setCustomer(customer);
        actionLog.setInvoice(invoice);
        actionLog.setPayment(payment);
        actionLog.setPppoeUsername(service.getPppoeUsername());
        actionLog.setActionType(disable ? "disable" : "enable");
        actionLog.setReason(reason);
        actionLog.setExecutionMode(executionMode);
        actionLog.setStatus("running");
        actionLog.setExecutedBy(resolveActor(executionMode));
        actionLog.setRequestPayload(toJson(Map.of(
                "customerId", customer.getId(),
                "pppoeUsername", service.getPppoeUsername(),
                "action", disable ? "disable" : "enable"
        )));
        actionLogRepository.save(actionLog);

        try {
            MikrotikDevice device = resolveMikrotik(service);
            if (device == null) {
                throw new IllegalStateException("MikroTik pelanggan tidak ditemukan");
            }
            MikrotikConnectionService.ConnectionTarget target = resolveTarget(device);
            mikrotikRouterOsApiClient.setPppSecretDisabled(
                    target,
                    device.resolveApiUsername(),
                    device.resolveApiPassword(),
                    service.getPppoeUsername(),
                    disable
            );

            customer.setPppoeStatus(disable ? "disabled" : "active");
            customer.setStatus(disable ? "suspended" : "active");
            customer.setIsActive(!disable);
            customerRepository.save(customer);

            service.setStatus(disable ? "suspended" : "active");
            customerServiceRepository.save(service);

            actionLog.setStatus("success");
            actionLog.setExecutedAt(LocalDateTime.now());
            actionLog.setResponsePayload(toJson(Map.of(
                    "targetHost", target.host(),
                    "targetPort", target.port(),
                    "pppoeStatus", customer.getPppoeStatus()
            )));
            actionLogRepository.save(actionLog);

            billingAuditTrailService.record(
                    disable ? "PPPOE_DISABLE" : "PPPOE_ENABLE",
                    (disable ? "Disable" : "Enable") + " PPPoE untuk pelanggan " + customer.getCustomerCode()
                            + " (" + service.getPppoeUsername() + ")"
            );

            return Map.of(
                    "customerId", customer.getId(),
                    "pppoeUsername", service.getPppoeUsername(),
                    "status", "success",
                    "pppoeStatus", customer.getPppoeStatus(),
                    "executedAt", actionLog.getExecutedAt()
            );
        } catch (Exception ex) {
            actionLog.setStatus("failed");
            actionLog.setExecutedAt(LocalDateTime.now());
            actionLog.setErrorMessage(ex.getMessage());
            actionLogRepository.save(actionLog);
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private Map<String, Object> toggleCustomerWithoutLog(Customer customer,
                                                         CustomerServiceEntity service,
                                                         String reason,
                                                         boolean disable) {
        try {
            MikrotikDevice device = resolveMikrotik(service);
            if (device == null) {
                throw new IllegalStateException("MikroTik pelanggan tidak ditemukan");
            }
            MikrotikConnectionService.ConnectionTarget target = resolveTarget(device);
            mikrotikRouterOsApiClient.setPppSecretDisabled(
                    target,
                    device.resolveApiUsername(),
                    device.resolveApiPassword(),
                    service.getPppoeUsername(),
                    disable
            );

            customer.setPppoeStatus(disable ? "disabled" : "active");
            customer.setStatus(disable ? "suspended" : "active");
            customer.setIsActive(!disable);
            customerRepository.save(customer);

            service.setStatus(disable ? "suspended" : "active");
            customerServiceRepository.save(service);

            billingAuditTrailService.record(
                    disable ? "PPPOE_DISABLE" : "PPPOE_ENABLE",
                    (disable ? "Disable" : "Enable") + " PPPoE untuk pelanggan " + customer.getCustomerCode()
                            + " (" + service.getPppoeUsername() + ")"
                            + " tanpa action log karena tabel pppoe_action_logs belum tersedia"
                            + (StringUtils.hasText(reason) ? ". Alasan: " + reason.trim() : "")
            );

            return Map.of(
                    "customerId", customer.getId(),
                    "pppoeUsername", service.getPppoeUsername(),
                    "status", "success",
                    "pppoeStatus", customer.getPppoeStatus()
            );
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    private CustomerServiceEntity latestService(Long customerId) {
        return customerServiceRepository.findTopByCustomerIdOrderByCreatedAtDesc(customerId).orElse(null);
    }

    private MikrotikDevice resolveMikrotik(CustomerServiceEntity service) {
        Odp odp = service.getOdp();
        if (odp == null) {
            return null;
        }
        Odc odc = odp.getOdc();
        if (odc == null) {
            return null;
        }
        Server server = odc.getServer();
        if (server == null || server.getMikrotikId() == null) {
            return null;
        }
        return mikrotikDeviceRepository.findById(server.getMikrotikId()).orElse(null);
    }

    private MikrotikConnectionService.ConnectionTarget resolveTarget(MikrotikDevice device) {
        return mikrotikConnectionService.resolveTarget(
                device.getApiIpAddress(),
                device.getApiPort() != null ? device.getApiPort() : 8728
        );
    }

    private String resolveActor(String executionMode) {
        return "system".equalsIgnoreCase(executionMode) ? "system" : operatorContextService.currentActor();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private boolean tableExists(String tableName) {
        try {
            String relation = jdbcTemplate.queryForObject("select to_regclass(?)", String.class, tableName);
            return relation != null && !relation.isBlank();
        } catch (DataAccessException ex) {
            log.warn("Unable to verify relation {} existence. Assuming unavailable for safety.", tableName, ex);
            return false;
        }
    }
}
