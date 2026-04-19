package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryTransactionView {
    private Long id;
    private String transactionCode;
    private String type;
    private Integer quantity;
    private String reference;
    private String notes;
    private LocalDateTime createdAt;
    private Long itemId;
    private String itemCode;
    private String itemName;
    private String category;
    private Long technicianId;
    private String technicianName;
    private Long createdById;
    private String createdByName;
}
