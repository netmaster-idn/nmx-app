package com.netmaster.nmx.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OdcOptionView {
    Long id;
    String name;
    String code;
    String location;
    Long serverId;
    String serverName;
    Integer capacity;
    Integer usedPort;
}
