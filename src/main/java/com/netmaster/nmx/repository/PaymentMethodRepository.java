package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findByCompanyProfileIdAndIsActiveTrueOrderByIdAsc(Long companyProfileId);
}
