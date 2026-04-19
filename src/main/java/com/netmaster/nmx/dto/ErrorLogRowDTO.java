package com.netmaster.nmx.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorLogRowDTO {
    private Long id;
    private LocalDateTime createdAt;
    private String username;
    private String fullName;
    private String roleNames;
    private String moduleName;
    private String actionLabel;
    private String httpMethod;
    private String requestPath;
    private Integer statusCode;
    private String errorType;
    private String errorMessage;
    private String rootCauseMessage;
    private String stackTrace;
    private String ipAddress;
}
