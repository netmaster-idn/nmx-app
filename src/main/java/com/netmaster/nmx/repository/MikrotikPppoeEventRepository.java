package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.MikrotikPppoeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MikrotikPppoeEventRepository extends JpaRepository<MikrotikPppoeEvent, Long> {

    @Query("""
            select e from MikrotikPppoeEvent e
            join fetch e.device d
            order by e.eventTime desc
            """)
    List<MikrotikPppoeEvent> findTop100ByOrderByEventTimeDesc();

    @Query("""
            select e from MikrotikPppoeEvent e
            join fetch e.device d
            where d.id = :deviceId
            order by e.eventTime desc
            """)
    List<MikrotikPppoeEvent> findTop100ByDeviceIdOrderByEventTimeDesc(@Param("deviceId") Long deviceId);

    Optional<MikrotikPppoeEvent> findByFingerprintHash(String fingerprintHash);

    @Query("""
            select e from MikrotikPppoeEvent e
            join fetch e.device d
            order by e.eventTime desc nulls last, e.id desc
            """)
    List<MikrotikPppoeEvent> findAllWithDeviceOrderByEventTimeDesc();

    @Query("""
            select e from MikrotikPppoeEvent e
            join fetch e.device d
            where d.id = :deviceId
            order by e.eventTime desc nulls last, e.id desc
            """)
    List<MikrotikPppoeEvent> findAllWithDeviceByDeviceIdOrderByEventTimeDesc(@Param("deviceId") Long deviceId);

    void deleteByCreatedAtBefore(LocalDateTime threshold);
}
