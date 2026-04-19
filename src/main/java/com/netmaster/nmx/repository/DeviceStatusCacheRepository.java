package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.DeviceStatusCache;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeviceStatusCacheRepository extends JpaRepository<DeviceStatusCache, Long> {

    Optional<DeviceStatusCache> findByDeviceId(Long deviceId);

    @Query("""
            select c from DeviceStatusCache c
            where (:deviceType is null or lower(coalesce(c.role, '')) = lower(:deviceType))
              and (:location is null or lower(coalesce(c.location, '')) = lower(:location))
              and (:status is null or lower(coalesce(c.status, '')) = lower(:status))
              and (:freshness is null or lower(coalesce(c.freshnessStatus, '')) = lower(:freshness))
              and (:search is null
                    or lower(coalesce(c.deviceName, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(c.ipAddress, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(c.role, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(c.location, '')) like lower(concat('%', :search, '%')))
            """)
    Page<DeviceStatusCache> search(
            @Param("deviceType") String deviceType,
            @Param("location") String location,
            @Param("status") String status,
            @Param("freshness") String freshness,
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            select distinct c.location from DeviceStatusCache c
            where c.location is not null and c.location <> ''
            order by c.location asc
            """)
    List<String> findDistinctLocations();

    @Query("""
            select distinct c.role from DeviceStatusCache c
            where c.role is not null and c.role <> ''
            order by c.role asc
            """)
    List<String> findDistinctRoles();

    List<DeviceStatusCache> findTop5ByFreshnessStatusOrderByLastSyncAtAsc(String freshnessStatus);

    List<DeviceStatusCache> findTop5ByStatusOrderByLastSyncAtAsc(String status);

    List<DeviceStatusCache> findTop5ByOrderByAlertCountDescHealthScoreAsc();
}
