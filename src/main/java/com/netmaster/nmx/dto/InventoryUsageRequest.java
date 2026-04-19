package com.netmaster.nmx.dto;

import lombok.Data;

@Data
public class InventoryUsageRequest {
    private Integer quantity;
    private Long technicianId;
    private String reference;
    private String notes;
}
