package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.NetworkDeviceSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NetworkDeviceSyncStatusRepository extends JpaRepository<NetworkDeviceSyncStatus, Long> {

    Optional<NetworkDeviceSyncStatus> findByDeviceIdAndModuleName(Long deviceId, String moduleName);

    List<NetworkDeviceSyncStatus> findByDeviceIdOrderByModuleNameAsc(Long deviceId);

    List<NetworkDeviceSyncStatus> findByModuleNameOrderByLastAttemptAtDesc(String moduleName);
}
