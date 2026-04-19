package com.netmaster.nmx.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WhatsappReminderSettingsRequest {

    @NotNull
    private Boolean enabled;

    @NotNull
    private Integer leadDays;
}
