package com.netmaster.nmx.model;

import org.springframework.web.util.UriUtils;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;

@Entity
@Table(name = "company_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "province_code", length = 20)
    private String provinceCode;

    @Column(name = "province_name", length = 100)
    private String provinceName;

    @Column(name = "regency_code", length = 20)
    private String regencyCode;

    @Column(name = "regency_name", length = 100)
    private String regencyName;

    @Column(name = "district_code", length = 20)
    private String districtCode;

    @Column(name = "district_name", length = 100)
    private String districtName;

    @Column(name = "village_code", length = 20)
    private String villageCode;

    @Column(name = "village_name", length = 100)
    private String villageName;

    @Column(name = "rt", length = 10)
    private String rt;

    @Column(name = "rw", length = 10)
    private String rw;

    @Column(name = "building_number", length = 50)
    private String buildingNumber;

    @Column(name = "street_name", length = 200)
    private String streetName;

    @Column(name = "google_maps_coordinates", length = 100)
    private String googleMapsCoordinates;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "website", length = 100)
    private String website;

    @Column(name = "logo", length = 500)
    private String logo;

    @Column(name = "npwp", length = 50)
    private String npwp;

    @Column(name = "pkp_number", length = 50)
    private String pkpNumber;

    @Column(name = "business_type", length = 200)
    private String businessType;

    @Column(name = "tagline", length = 200)
    private String tagline;

    @Column(name = "facebook")
    private String facebook;

    @Column(name = "instagram")
    private String instagram;

    @Column(name = "twitter")
    private String twitter;

    @Column(name = "whatsapp", length = 20)
    private String whatsapp;

    @Column(name = "support_email", length = 100)
    private String supportEmail;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Transient
    public String getLogoUrl() {
        if (logo == null || logo.isBlank()) {
            return null;
        }
        return "/api/company/logo/" + UriUtils.encodePathSegment(logo, StandardCharsets.UTF_8);
    }
}

