package com.netmaster.nmx.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEditDetailDTO {
    private Long id;
    private String customerCode;
    private String fullName;
    private String email;
    private String phone;
    private String ktpNumber;
    private String ktpAddress;
    private String installationAddress;
    private Long regionId;
    private Long companyProfileId;
    private Long serverId;
    private Long odcId;
    private Long odpId;
    private Long packageId;
    private String packageName;
    private BigDecimal monthlyFee;
    private Integer odpPort;
    private String latitude;
    private String longitude;
}
