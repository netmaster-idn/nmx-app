package com.netmaster.nmx.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRegistrationDTO {
    
    // Customer Data
    @NotBlank(message = "Nama pelanggan wajib diisi")
    @Size(max = 100, message = "Nama pelanggan maksimal 100 karakter")
    private String fullName;

    @Email(message = "Format email tidak valid")
    private String email;

    @NotBlank(message = "Nomor telepon wajib diisi")
    @Size(max = 20, message = "Nomor telepon maksimal 20 karakter")
    private String phone;

    @Size(max = 20, message = "Nomor KTP maksimal 20 karakter")
    private String ktpNumber;

    private String ktpAddress;

    @NotBlank(message = "Alamat instalasi wajib diisi")
    private String installationAddress;
    private Long regionId;
    private Long companyProfileId;
    private Long serverId;
    private Long odcId;

    @Pattern(regexp = "^-?\\d{0,3}(\\.\\d+)?$", message = "Latitude tidak valid")
    private String latitude;

    @Pattern(regexp = "^-?\\d{0,3}(\\.\\d+)?$", message = "Longitude tidak valid")
    private String longitude;
    
    // Service Data
    private Long packageId;
    private Long serviceTypeId;
    private Long odpId;
    private Integer odpPort;
    
    // Technical Data
    private String ontSerial;
    private String ontBrand;
    private String pppoeUsername;
    private String pppoePassword;

    @Pattern(regexp = "^$|^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$", message = "IP address tidak valid")
    private String ipAddress;

    @Pattern(regexp = "^$|^([0-9A-Fa-f]{2}([-:])){5}[0-9A-Fa-f]{2}$", message = "MAC address tidak valid")
    private String macAddress;
    
    // Billing Data
    @DecimalMin(value = "0.0", inclusive = true, message = "Biaya bulanan tidak boleh negatif")
    private BigDecimal monthlyFee;

    @DecimalMin(value = "0.0", inclusive = true, message = "Biaya instalasi tidak boleh negatif")
    private BigDecimal installationFee;
    private LocalDate installationDate;
    private Long technicianId;
    private String notes;
}

