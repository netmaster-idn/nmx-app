package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.Server;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ServerRepository extends JpaRepository<Server, Long> {
    List<Server> findByIsActiveTrueOrderByNameAsc();
    List<Server> findByRegionIdOrderByNameAsc(Long regionId);
    Optional<Server> findByNameIgnoreCase(String name);
    Optional<Server> findByIpAddress(String ipAddress);
}

