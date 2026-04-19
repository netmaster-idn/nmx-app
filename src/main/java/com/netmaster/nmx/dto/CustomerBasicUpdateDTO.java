package com.netmaster.nmx.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerBasicUpdateDTO {
    @NotBlank(message = "Nama pelanggan wajib diisi")
    private String fullName;

    @NotBlank(message = "Nomor telepon wajib diisi")
    private String phone;

    @Email(message = "Format email tidak valid")
    private String email;

    @NotBlank(message = "Alamat instalasi wajib diisi")
    private String installationAddress;

    private String ktpNumber;
    private String ktpAddress;
    private Long companyProfileId;
    private Long serverId;
    private Long odcId;
    private Long odpId;
    private Long packageId;
    private Integer odpPort;

    @Pattern(regexp = "^-?\\d{0,3}(\\.\\d+)?$", message = "Latitude tidak valid")
    private String latitude;

    @Pattern(regexp = "^-?\\d{0,3}(\\.\\d+)?$", message = "Longitude tidak valid")
    private String longitude;
}
