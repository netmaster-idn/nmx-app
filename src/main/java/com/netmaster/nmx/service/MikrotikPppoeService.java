package com.netmaster.nmx.service;

import com.netmaster.nmx.model.Customer;
import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.model.MikrotikDevice;
import com.netmaster.nmx.model.Odc;
import com.netmaster.nmx.model.Odp;
import com.netmaster.nmx.model.Server;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import com.netmaster.nmx.repository.MikrotikDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MikrotikPppoeService {

    private final CustomerServiceEntityRepository customerServiceEntityRepository;
    private final MikrotikDeviceRepository mikrotikDeviceRepository;
    private final MikrotikConnectionService mikrotikConnectionService;
    private final MikrotikRouterOsApiClient mikrotikRouterOsApiClient;

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public void disableCustomerSecret(Customer customer) {
        toggleSecret(customer, true);
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public void enableCustomerSecret(Customer customer) {
        toggleSecret(customer, false);
    }

    private void toggleSecret(Customer customer, boolean disabled) {
        if (customer == null || customer.getId() == null) {
            return;
        }

        CustomerServiceEntity service = customerServiceEntityRepository
                .findTopByCustomerIdOrderByCreatedAtDesc(customer.getId())
                .orElse(null);
        if (service == null || !StringUtils.hasText(service.getPppoeUsername())) {
            return;
        }

        MikrotikDevice mikrotik = resolveMikrotik(service);
        if (mikrotik == null) {
            throw new IllegalStateException("MikroTik untuk pelanggan tidak ditemukan.");
        }
        if (!StringUtils.hasText(mikrotik.resolveApiUsername()) || !StringUtils.hasText(mikrotik.resolveApiPassword())) {
            throw new IllegalStateException("Kredensial API MikroTik belum lengkap.");
        }

        MikrotikConnectionService.ResolvedTarget target = new MikrotikConnectionService.ResolvedTarget(
                "api",
                mikrotikConnectionService.resolveTarget(
                        mikrotik.getApiIpAddress(),
                        mikrotik.getApiPort() != null ? mikrotik.getApiPort() : 8728
                )
        );

        mikrotikRouterOsApiClient.setPppSecretDisabled(
                target.target(),
                mikrotik.resolveApiUsername(),
                mikrotik.resolveApiPassword(),
                service.getPppoeUsername(),
                disabled
        );
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
}
