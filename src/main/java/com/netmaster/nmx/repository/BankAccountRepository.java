package com.netmaster.nmx.repository;

import com.netmaster.nmx.model.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, Long> {

    Optional<BankAccount> findFirstByCompanyProfileIdAndIsPrimaryTrueAndIsActiveTrueOrderByIdAsc(Long companyProfileId);

    Optional<BankAccount> findFirstByCompanyProfileIdAndIsActiveTrueOrderByIsPrimaryDescIdAsc(Long companyProfileId);

    List<BankAccount> findByCompanyProfileIdAndIsActiveTrueOrderByIsPrimaryDescIdAsc(Long companyProfileId);
}
