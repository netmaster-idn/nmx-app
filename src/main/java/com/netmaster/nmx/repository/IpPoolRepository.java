package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.IpPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IpPoolRepository extends JpaRepository<IpPool, Long> {
    
    Optional<IpPool> findByPoolName(String poolName);
    
    List<IpPool> findByIsActiveTrue();
    
    List<IpPool> findByVlan(Integer vlan);
}

