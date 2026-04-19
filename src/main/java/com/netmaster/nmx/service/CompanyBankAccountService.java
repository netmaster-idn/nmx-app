package com.netmaster.nmx.service;

import com.netmaster.nmx.dto.BankAccountRequest;
import com.netmaster.nmx.dto.BankAccountView;
import com.netmaster.nmx.model.BankAccount;
import com.netmaster.nmx.model.CompanyProfile;
import com.netmaster.nmx.repository.BankAccountRepository;
import com.netmaster.nmx.repository.CompanyProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CompanyBankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final CompanyProfileRepository companyProfileRepository;

    @Transactional(readOnly = true)
    public List<BankAccountView> getAccounts(Long companyId) {
        return bankAccountRepository.findByCompanyProfileIdAndIsActiveTrueOrderByIsPrimaryDescIdAsc(companyId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public BankAccountView createAccount(Long companyId, BankAccountRequest request) {
        CompanyProfile company = companyProfileRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company tidak ditemukan"));

        boolean setPrimary = Boolean.TRUE.equals(request.getPrimary())
                || bankAccountRepository.findByCompanyProfileIdAndIsActiveTrueOrderByIsPrimaryDescIdAsc(companyId).isEmpty();

        if (setPrimary) {
            clearPrimaryFlag(companyId);
        }

        BankAccount account = new BankAccount();
        account.setCompanyProfile(company);
        account.setBankName(cleanRequired(request.getBankName(), "Nama bank wajib diisi"));
        account.setAccountName(cleanRequired(request.getAccountName(), "Nama pemilik rekening wajib diisi"));
        account.setAccountNumber(cleanRequired(request.getAccountNumber(), "Nomor rekening wajib diisi"));
        account.setInstructions(clean(request.getInstructions()));
        account.setIsPrimary(setPrimary);
        account.setIsActive(true);
        return toView(bankAccountRepository.save(account));
    }

    @Transactional
    public BankAccountView updateAccount(Long companyId, Long accountId, BankAccountRequest request) {
        BankAccount account = getOwnedAccount(companyId, accountId);
        boolean setPrimary = Boolean.TRUE.equals(request.getPrimary());
        if (setPrimary) {
            clearPrimaryFlag(companyId);
        }

        account.setBankName(cleanRequired(request.getBankName(), "Nama bank wajib diisi"));
        account.setAccountName(cleanRequired(request.getAccountName(), "Nama pemilik rekening wajib diisi"));
        account.setAccountNumber(cleanRequired(request.getAccountNumber(), "Nomor rekening wajib diisi"));
        account.setInstructions(clean(request.getInstructions()));
        account.setIsPrimary(setPrimary || isOnlyActiveAccount(companyId, accountId));
        account.setIsActive(true);
        return toView(bankAccountRepository.save(account));
    }

    @Transactional
    public void deleteAccount(Long companyId, Long accountId) {
        BankAccount account = getOwnedAccount(companyId, accountId);
        boolean wasPrimary = Boolean.TRUE.equals(account.getIsPrimary());
        account.setIsActive(false);
        account.setIsPrimary(false);
        bankAccountRepository.save(account);

        if (wasPrimary) {
            List<BankAccount> remaining = bankAccountRepository.findByCompanyProfileIdAndIsActiveTrueOrderByIsPrimaryDescIdAsc(companyId);
            if (!remaining.isEmpty()) {
                BankAccount nextPrimary = remaining.get(0);
                nextPrimary.setIsPrimary(true);
                bankAccountRepository.save(nextPrimary);
            }
        }
    }

    private void clearPrimaryFlag(Long companyId) {
        List<BankAccount> accounts = bankAccountRepository.findByCompanyProfileIdAndIsActiveTrueOrderByIsPrimaryDescIdAsc(companyId);
        for (BankAccount account : accounts) {
            if (Boolean.TRUE.equals(account.getIsPrimary())) {
                account.setIsPrimary(false);
            }
        }
        bankAccountRepository.saveAll(accounts);
    }

    private BankAccount getOwnedAccount(Long companyId, Long accountId) {
        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Rekening bank tidak ditemukan"));
        Long accountCompanyId = account.getCompanyProfile() != null ? account.getCompanyProfile().getId() : null;
        if (!Objects.equals(companyId, accountCompanyId)) {
            throw new IllegalArgumentException("Rekening bank tidak sesuai dengan company");
        }
        return account;
    }

    private boolean isOnlyActiveAccount(Long companyId, Long accountId) {
        List<BankAccount> accounts = bankAccountRepository.findByCompanyProfileIdAndIsActiveTrueOrderByIsPrimaryDescIdAsc(companyId);
        return accounts.stream().filter(item -> !Objects.equals(item.getId(), accountId)).findAny().isEmpty();
    }

    private BankAccountView toView(BankAccount account) {
        return new BankAccountView(
                account.getId(),
                account.getBankName(),
                account.getAccountName(),
                account.getAccountNumber(),
                account.getInstructions(),
                Boolean.TRUE.equals(account.getIsPrimary())
        );
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String cleanRequired(String value, String message) {
        String cleaned = clean(value);
        if (!StringUtils.hasText(cleaned)) {
            throw new IllegalArgumentException(message);
        }
        return cleaned;
    }
}
