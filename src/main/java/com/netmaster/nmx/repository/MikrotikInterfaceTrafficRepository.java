package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.MikrotikInterfaceTraffic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MikrotikInterfaceTrafficRepository extends JpaRepository<MikrotikInterfaceTraffic, Long> {

    Optional<MikrotikInterfaceTraffic> findTopByMikrotikInterfaceIdOrderByCollectedAtDesc(Long interfaceId);

    @Query("""
            select t from MikrotikInterfaceTraffic t
            join fetch t.device d
            join fetch t.mikrotikInterface i
            where t.collectedAt >= :since
            order by t.collectedAt desc
            """)
    List<MikrotikInterfaceTraffic> findRecentWithDeviceAndInterface(@Param("since") LocalDateTime since);

    List<MikrotikInterfaceTraffic> findByMikrotikInterfaceIdAndCollectedAtBetweenOrderByCollectedAtAsc(
            Long interfaceId,
            LocalDateTime start,
            LocalDateTime end
    );

    @Query("""
            select t from MikrotikInterfaceTraffic t
            join fetch t.device d
            join fetch t.mikrotikInterface i
            where i.monitored = true
            order by t.collectedAt desc
            """)
    List<MikrotikInterfaceTraffic> findLatestMonitoredTraffic(Pageable pageable);

    void deleteByCollectedAtBefore(LocalDateTime threshold);
}
