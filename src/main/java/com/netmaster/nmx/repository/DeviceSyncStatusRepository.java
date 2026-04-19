package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.DeviceSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceSyncStatusRepository extends JpaRepository<DeviceSyncStatus, Long> {

    Optional<DeviceSyncStatus> findByDeviceIdAndModuleName(Long deviceId, String moduleName);

    @Query("""
            select s from DeviceSyncStatus s
            join fetch s.device d
            where s.moduleName = :moduleName
            """)
    List<DeviceSyncStatus> findByModuleName(@Param("moduleName") String moduleName);
}
