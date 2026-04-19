package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.DeviceMaintenanceWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeviceMaintenanceWindowRepository extends JpaRepository<DeviceMaintenanceWindow, Long> {

    @Query("""
            select m from DeviceMaintenanceWindow m
            join fetch m.device d
            where m.startsAt <= :now and m.endsAt >= :now
            order by m.startsAt asc
            """)
    List<DeviceMaintenanceWindow> findActiveWindows(@Param("now") LocalDateTime now);

    @Query("""
            select m from DeviceMaintenanceWindow m
            where m.device.id = :deviceId and m.startsAt <= :now and m.endsAt >= :now
            order by m.startsAt desc
            """)
    Optional<DeviceMaintenanceWindow> findActiveWindowByDeviceId(@Param("deviceId") Long deviceId, @Param("now") LocalDateTime now);

    List<DeviceMaintenanceWindow> findByEndsAtAfterOrderByStartsAtAsc(LocalDateTime endsAt);
}
