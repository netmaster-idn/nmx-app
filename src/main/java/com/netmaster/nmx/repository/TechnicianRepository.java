package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.Technician;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TechnicianRepository extends JpaRepository<Technician, Long> {
    List<Technician> findByIsActiveTrueOrderByNameAsc();
    List<Technician> findAllByOrderByNameAsc();
}

