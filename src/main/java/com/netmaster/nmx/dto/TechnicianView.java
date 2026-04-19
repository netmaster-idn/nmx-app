package com.netmaster.nmx.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TechnicianView {
    Long id;
    String name;
    String initials;
    String area;
    String phone;
    String status;
    boolean isActive;
    long activeTickets;
    double rating;
}
