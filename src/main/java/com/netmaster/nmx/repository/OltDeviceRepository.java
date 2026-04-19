package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.OltDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OltDeviceRepository extends JpaRepository<OltDevice, Long> {
    
    Optional<OltDevice> findByIpAddress(String ipAddress);
    
    List<OltDevice> findByStatus(String status);
    
    List<OltDevice> findByIsActiveTrue();
    
    List<OltDevice> findByVendor(String vendor);

    @Query("""
            select d from OltDevice d
            where lower(coalesce(d.name, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(d.location, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(d.ipAddress, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(d.vpnIpAddress, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(d.username, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(d.vendor, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(d.model, '')) like lower(concat('%', :keyword, '%'))
            order by d.updatedAt desc, d.id desc
            """)
    List<OltDevice> searchByKeyword(@Param("keyword") String keyword);
}

