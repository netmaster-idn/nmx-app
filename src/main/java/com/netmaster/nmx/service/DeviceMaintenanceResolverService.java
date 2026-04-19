package com.netmaster.nmx.service;

import com.netmaster.nmx.model.DeviceMaintenanceWindow;
import com.netmaster.nmx.repository.DeviceMaintenanceWindowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceMaintenanceResolverService {

    private final DeviceMaintenanceWindowRepository maintenanceWindowRepository;

    @Scheduled(fixedDelay = 60000, initialDelay = 15000)
    @Transactional
    public void refreshActiveFlags() {
        LocalDateTime now = LocalDateTime.now();
        List<DeviceMaintenanceWindow> windows = maintenanceWindowRepository.findAll();
        for (DeviceMaintenanceWindow window : windows) {
            boolean active = !window.getStartsAt().isAfter(now) && !window.getEndsAt().isBefore(now);
            if (!Boolean.valueOf(active).equals(window.getIsActive())) {
                window.setIsActive(active);
            }
        }
        if (!windows.isEmpty()) {
            log.debug("Resolved {} maintenance windows", windows.size());
        }
    }
}
