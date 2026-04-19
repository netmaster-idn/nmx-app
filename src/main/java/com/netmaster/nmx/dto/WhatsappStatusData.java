package com.netmaster.nmx.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WhatsappStatusData {

    private String status;
    private String label;
    @JsonProperty("isReady")
    private boolean ready;
    private boolean hasQr;
    private String qrCode;
    private String sessionId;
    private String lastConnectedAt;
    private String lastDisconnectedAt;
    private String lastError;
    private String qrUpdatedAt;
}
