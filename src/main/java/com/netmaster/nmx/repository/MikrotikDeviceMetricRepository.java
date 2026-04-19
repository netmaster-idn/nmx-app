package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.MikrotikDeviceMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MikrotikDeviceMetricRepository extends JpaRepository<MikrotikDeviceMetric, Long> {

    Optional<MikrotikDeviceMetric> findTopByDeviceIdOrderByCollectedAtDesc(Long deviceId);

    @Query("""
            select m from MikrotikDeviceMetric m
            join fetch m.device d
            where m.device.id in :deviceIds
              and m.collectedAt = (
                select max(m2.collectedAt)
                from MikrotikDeviceMetric m2
                where m2.device.id = m.device.id
              )
            """)
    List<MikrotikDeviceMetric> findLatestByDeviceIds(@Param("deviceIds") List<Long> deviceIds);

    List<MikrotikDeviceMetric> findByDeviceIdAndCollectedAtBetweenOrderByCollectedAtAsc(
            Long deviceId,
            LocalDateTime start,
            LocalDateTime end
    );
}
