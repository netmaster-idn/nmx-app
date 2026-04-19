package com.netmaster.nmx.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OdpOptionView {
    Long id;
    String name;
    String code;
    String nodeType;
    String location;
    String splitter;
    Integer capacity;
    Integer usedPort;
    Long odcId;
    String odcName;
    Long serverId;
    String serverName;
    Long companyProfileId;
    String companyProfileName;
}
