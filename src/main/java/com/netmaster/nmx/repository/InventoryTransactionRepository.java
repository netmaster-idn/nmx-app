package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.InventoryTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    @EntityGraph(attributePaths = {"item", "technician", "createdBy"})
    List<InventoryTransaction> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"item", "technician", "createdBy"})
    List<InventoryTransaction> findByItemIdOrderByCreatedAtDesc(Long itemId);

    Optional<InventoryTransaction> findTopByTransactionCodeStartingWithOrderByTransactionCodeDesc(String transactionCodePrefix);

    List<InventoryTransaction> findByType(String type);

    List<InventoryTransaction> findByReferenceContaining(String reference);

    List<InventoryTransaction> findByCreatedById(Long userId);

    long countByItemId(Long itemId);

    long countByType(String type);

    long countByTechnicianId(Long technicianId);
}

