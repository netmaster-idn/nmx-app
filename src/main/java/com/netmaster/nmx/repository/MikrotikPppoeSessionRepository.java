package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.MikrotikPppoeSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MikrotikPppoeSessionRepository extends JpaRepository<MikrotikPppoeSession, Long> {

    Optional<MikrotikPppoeSession> findByDeviceIdAndUsername(Long deviceId, String username);

    @Query("""
            select s from MikrotikPppoeSession s
            join fetch s.device d
            where s.status = :status
            order by s.lastSyncAt desc
            """)
    List<MikrotikPppoeSession> findByStatusOrderByLastSyncAtDesc(@Param("status") String status);

    @Query("""
            select s from MikrotikPppoeSession s
            join fetch s.device d
            where d.id = :deviceId
            order by s.lastSyncAt desc
            """)
    List<MikrotikPppoeSession> findByDeviceIdOrderByLastSyncAtDesc(@Param("deviceId") Long deviceId);

    @Query("""
            select s from MikrotikPppoeSession s
            join fetch s.device d
            order by s.lastSyncAt desc nulls last, s.id desc
            """)
    List<MikrotikPppoeSession> findAllWithDeviceOrderByLastSyncAtDesc();

    @Query("""
            select s from MikrotikPppoeSession s
            join fetch s.device d
            where d.id = :deviceId
            order by s.lastSyncAt desc nulls last, s.id desc
            """)
    List<MikrotikPppoeSession> findAllWithDeviceByDeviceIdOrderByLastSyncAtDesc(@Param("deviceId") Long deviceId);
}
