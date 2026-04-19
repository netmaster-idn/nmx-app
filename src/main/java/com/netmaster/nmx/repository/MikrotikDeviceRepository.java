package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.MikrotikDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MikrotikDeviceRepository extends JpaRepository<MikrotikDevice, Long> {
    
    Optional<MikrotikDevice> findByIpAddress(String ipAddress);
    
    List<MikrotikDevice> findByStatus(String status);
    
    List<MikrotikDevice> findByIsActiveTrue();

    long countByIsActiveTrue();

    @Query("""
            select d from MikrotikDevice d
            where d.isActive = true
            order by coalesce(d.createdAt, d.updatedAt) asc, d.id asc
            """)
    List<MikrotikDevice> findActiveRoutersOrdered();

    Optional<MikrotikDevice> findFirstByIsActiveTrueOrderByCreatedAtAscIdAsc();
    
    List<MikrotikDevice> findByLocationContaining(String location);

    @Query("""
            select d from MikrotikDevice d
            where lower(coalesce(d.deviceName, d.name, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(d.siteName, d.location, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(d.location, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(d.ipAddress, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(d.vpnIpAddress, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(d.winboxIpAddress, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(d.apiIpAddress, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(d.apiUsername, d.username, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(d.username, '')) like lower(concat('%', :keyword, '%'))
            order by d.updatedAt desc, d.id desc
            """)
    List<MikrotikDevice> searchByKeyword(@Param("keyword") String keyword);
}

