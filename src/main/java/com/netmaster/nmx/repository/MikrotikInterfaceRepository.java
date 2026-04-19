package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.MikrotikInterface;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MikrotikInterfaceRepository extends JpaRepository<MikrotikInterface, Long> {

    Optional<MikrotikInterface> findByDeviceIdAndInterfaceIndex(Long deviceId, Integer interfaceIndex);

    List<MikrotikInterface> findByDeviceIdOrderByInterfaceNameAsc(Long deviceId);

    List<MikrotikInterface> findByMonitoredTrueOrderByPriorityAscInterfaceNameAsc();

    @Query("""
            select i from MikrotikInterface i
            join fetch i.device d
            where i.monitored = true
            order by i.priority asc, i.interfaceName asc
            """)
    List<MikrotikInterface> findMonitoredWithDevice();
}
