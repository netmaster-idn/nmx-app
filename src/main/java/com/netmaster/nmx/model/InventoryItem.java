package com.netmaster.nmx.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_code", length = 50, unique = true)
    private String itemCode;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "category", length = 50)
    private String category; // ONT, KABEL, ODP, AKSESORIS, TOOLS

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "unit", length = 20)
    private String unit; // pcs, roll, meter, box

    @Column(name = "min_stock")
    private Integer minStock;

    @Column(name = "current_stock")
    private Integer currentStock = 0;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "warehouse", length = 100)
    private String warehouse;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

