package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    
    Optional<InventoryItem> findByItemCode(String itemCode);

    Optional<InventoryItem> findTopByItemCodeStartingWithOrderByItemCodeDesc(String itemCodePrefix);

    List<InventoryItem> findByCategory(String category);

    List<InventoryItem> findByIsActiveTrue();

    List<InventoryItem> findAllByOrderByUpdatedAtDesc();

    @Query("SELECT i FROM InventoryItem i WHERE i.currentStock <= i.minStock")
    List<InventoryItem> findLowStockItems();

    List<InventoryItem> findByLocationContaining(String location);

    @Query("""
            SELECT i
            FROM InventoryItem i
            WHERE LOWER(i.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(i.itemCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY i.updatedAt DESC, i.name ASC
            """)
    List<InventoryItem> searchByKeyword(@Param("keyword") String keyword);
}

