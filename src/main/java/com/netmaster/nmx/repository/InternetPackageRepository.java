package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.InternetPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InternetPackageRepository extends JpaRepository<InternetPackage, Long> {
    // Used by customer-facing dropdowns (registration, etc.)
    List<InternetPackage> findByIsActiveTrueOrderByPriceAsc();

    // Used by management pages to show active + inactive packages
    List<InternetPackage> findAllByOrderByPriceAsc();
}