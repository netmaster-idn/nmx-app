package com.netmaster.nmx.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryItemRequest {
    private String name;
    private String category;
    private String description;
    private String unit;
    private Integer minStock;
    private Integer currentStock;
    private BigDecimal price;
    private Long supplierId;
    private String location;
    private String warehouse;
    private Boolean active;
}
