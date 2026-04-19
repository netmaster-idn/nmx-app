package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhatsappSendDocumentRequest {

    private String to;
    private String mimeType;
    private String fileName;
    private String base64Data;
    private String caption;
}
