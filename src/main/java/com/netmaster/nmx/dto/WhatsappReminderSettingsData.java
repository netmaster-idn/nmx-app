package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WhatsappReminderSettingsData {

    private boolean enabled;
    private int leadDays;
    private int hourlyLimit;
    private int batchIntervalMinutes;
    private int sendStartHour;
    private String previewRule;
}
