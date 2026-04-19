package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityLogRowDTO {
    private Long id;
    private LocalDateTime createdAt;
    private String username;
    private String fullName;
    private String roleNames;
    private String moduleName;
    private String actionType;
    private String actionLabel;
    private String httpMethod;
    private String requestPath;
    private Integer statusCode;
    private String status;
    private String ipAddress;
    private Long durationMs;
}
