package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhatsappBotHistoryItemData {

    private Long logId;
    private Long invoiceId;
    private String invoiceNumber;
    private String customerName;
    private String customerCode;
    private String phoneNumber;
    private String documentType;
    private String documentLabel;
    private String dispatchStatus;
    private String dispatchStatusLabel;
    private String deliveryStatus;
    private String deliveryStatusLabel;
    private Integer ack;
    private boolean sent;
    private boolean delivered;
    private boolean read;
    private Integer leadDays;
    private String dispatchMode;
    private String dispatchModeLabel;
    private LocalDate scheduledForDate;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private String gatewayMessage;
    private String messageId;
}
