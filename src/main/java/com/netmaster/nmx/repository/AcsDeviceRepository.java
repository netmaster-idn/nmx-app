package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.AcsDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AcsDeviceRepository extends JpaRepository<AcsDevice, Long> {

    Optional<AcsDevice> findByAcsDeviceId(String acsDeviceId);
    
    Optional<AcsDevice> findBySerialNumber(String serialNumber);
    
    List<AcsDevice> findByStatus(String status);
    
    List<AcsDevice> findByVendor(String vendor);
    
    List<AcsDevice> findByIsActiveTrue();
    
    List<AcsDevice> findByOltId(Long oltId);
}

