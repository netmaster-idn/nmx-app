package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.AutomationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AutomationRuleRepository extends JpaRepository<AutomationRule, Long> {
    
    List<AutomationRule> findByIsActiveTrue();
    
    List<AutomationRule> findByTriggerType(String triggerType);
    
    List<AutomationRule> findByCreatedById(Long userId);
}

