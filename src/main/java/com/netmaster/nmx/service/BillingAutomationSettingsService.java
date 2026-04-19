package com.netmaster.nmx.service;

import com.netmaster.nmx.dto.BillingAutomationSettingsRequest;
import com.netmaster.nmx.model.BillingAutomationSetting;
import com.netmaster.nmx.repository.BillingAutomationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BillingAutomationSettingsService {

    private final BillingAutomationSettingRepository repository;
    private final BillingOperatorContextService operatorContextService;

    @Transactional(readOnly = true)
    public BillingAutomationSetting getOrCreate() {
        return repository.findFirstByIsActiveTrueOrderByIdAsc()
                .orElseGet(this::createDefault);
    }

    @Transactional
    public BillingAutomationSetting update(BillingAutomationSettingsRequest request) {
        BillingAutomationSetting setting = getOrCreate();
        setting.setAutoSendInvoice(request.autoSendInvoice());
        setting.setInvoiceSendDaysBeforeDue(request.invoiceSendDaysBeforeDue());
        setting.setAutoSendReceipt(request.autoSendReceipt());
        setting.setAutoDisablePppoe(request.autoDisablePppoe());
        setting.setLatePaymentDisableDays(request.latePaymentDisableDays());
        setting.setDisableMode(normalizeDisableMode(request.disableMode()));
        setting.setSendWarningBeforeDisable(request.sendWarningBeforeDisable());
        setting.setWarningTemplate(StringUtils.hasText(request.warningTemplate())
                ? request.warningTemplate().trim()
                : defaultWarningTemplate());
        setting.setAutoEnablePppoeAfterPayment(request.autoEnablePppoeAfterPayment());
        setting.setExecutionTime(parseTime(request.executionTime()));
        setting.setIsActive(request.isActive());
        setting.setUpdatedBy(operatorContextService.currentActor());
        return repository.save(setting);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> toPayload(BillingAutomationSetting setting) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", setting.getId());
        payload.put("autoSendInvoice", Boolean.TRUE.equals(setting.getAutoSendInvoice()));
        payload.put("invoiceSendDaysBeforeDue", setting.getInvoiceSendDaysBeforeDue());
        payload.put("autoSendReceipt", Boolean.TRUE.equals(setting.getAutoSendReceipt()));
        payload.put("autoDisablePppoe", Boolean.TRUE.equals(setting.getAutoDisablePppoe()));
        payload.put("latePaymentDisableDays", setting.getLatePaymentDisableDays());
        payload.put("disableMode", normalizeDisableMode(setting.getDisableMode()));
        payload.put("sendWarningBeforeDisable", Boolean.TRUE.equals(setting.getSendWarningBeforeDisable()));
        payload.put("warningTemplate", setting.getWarningTemplate());
        payload.put("autoEnablePppoeAfterPayment", Boolean.TRUE.equals(setting.getAutoEnablePppoeAfterPayment()));
        payload.put("executionTime", setting.getExecutionTime() != null ? setting.getExecutionTime().toString() : "09:00");
        payload.put("isActive", Boolean.TRUE.equals(setting.getIsActive()));
        payload.put("updatedBy", setting.getUpdatedBy());
        payload.put("updatedAt", setting.getUpdatedAt());
        return payload;
    }

    private BillingAutomationSetting createDefault() {
        BillingAutomationSetting setting = new BillingAutomationSetting();
        setting.setWarningTemplate(defaultWarningTemplate());
        return repository.save(setting);
    }

    private String defaultWarningTemplate() {
        return "Pelanggan terhormat, tagihan Anda sudah melewati jatuh tempo. Layanan dapat dinonaktifkan bila belum ada pembayaran.";
    }

    private String normalizeDisableMode(String disableMode) {
        String normalized = StringUtils.hasText(disableMode) ? disableMode.trim().toLowerCase() : "automatic";
        return "approval".equals(normalized) ? "approval" : "automatic";
    }

    private LocalTime parseTime(String value) {
        if (!StringUtils.hasText(value)) {
            return LocalTime.of(9, 0);
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Format execution_time harus HH:mm atau HH:mm:ss");
        }
    }
}
