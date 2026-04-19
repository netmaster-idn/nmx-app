package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.CrmContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrmContactRepository extends JpaRepository<CrmContact, Long> {
    
    List<CrmContact> findByStatus(String status);
    
    List<CrmContact> findBySource(String source);
    
    List<CrmContact> findByAssignedToId(Long userId);
    
    List<CrmContact> findByNameContaining(String name);
}

