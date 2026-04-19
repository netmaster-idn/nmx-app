package com.netmaster.nmx.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MikrotikDeviceUpsertRequest {

    private String deviceName;
    private String siteName;
    private String ipAddress;
    private String vpnIpAddress;
    private Integer apiPort;
    private String apiUsername;
    private String apiPassword;
    private String snmpVersion;
    private String snmpCommunity;
    private Integer snmpPort;
    private String winboxIp;
    private String monitoringTarget;
    private String notes;
    private Boolean isActive;
    private Integer pollingIntervalSnmp;
    private Integer syncIntervalApi;
    private Integer monitoringInterval;
    private Boolean snmpEnabled;
    private Boolean apiEnabled;
}
