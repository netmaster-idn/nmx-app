package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.MikrotikDevice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.NoSuchElementException;

@Repository
@RequiredArgsConstructor
public class RouterRepository {

    private final MikrotikDeviceRepository mikrotikDeviceRepository;

    public long countActiveRouters() {
        return mikrotikDeviceRepository.countByIsActiveTrue();
    }

    public List<MikrotikDevice> findActiveRoutersOrdered() {
        return mikrotikDeviceRepository.findActiveRoutersOrdered();
    }

    public MikrotikDevice findDefaultRouter() {
        return mikrotikDeviceRepository.findFirstByIsActiveTrueOrderByCreatedAtAscIdAsc()
                .orElseThrow(() -> new NoSuchElementException("Belum ada router Mikrotik aktif di database"));
    }

    public MikrotikDevice findActiveRouterById(Long routerId) {
        MikrotikDevice device = mikrotikDeviceRepository.findById(routerId)
                .orElseThrow(() -> new NoSuchElementException("Router Mikrotik tidak ditemukan"));
        if (!device.isActive()) {
            throw new NoSuchElementException("Router Mikrotik tidak aktif");
        }
        return device;
    }
}
