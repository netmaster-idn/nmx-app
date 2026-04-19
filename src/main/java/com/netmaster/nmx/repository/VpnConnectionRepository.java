package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.VpnConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VpnConnectionRepository extends JpaRepository<VpnConnection, Long> {
    
    Optional<VpnConnection> findByName(String name);
    
    List<VpnConnection> findByStatus(String status);
    
    List<VpnConnection> findByVpnType(String vpnType);
    
    List<VpnConnection> findByIsActiveTrue();
}

